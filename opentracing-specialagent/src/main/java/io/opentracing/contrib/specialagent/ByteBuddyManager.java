/* Copyright 2018 The OpenTracing Authors
 *
 * Licensed under the Apache License, final Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, final either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentracing.contrib.specialagent;

import static net.bytebuddy.matcher.ElementMatchers.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.agent.builder.AgentBuilder.Default;
import net.bytebuddy.agent.builder.AgentBuilder.InitializationStrategy;
import net.bytebuddy.agent.builder.AgentBuilder.Listener;
import net.bytebuddy.agent.builder.AgentBuilder.RedefinitionStrategy;
import net.bytebuddy.agent.builder.AgentBuilder.TypeStrategy;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.scaffold.TypeValidation;
import net.bytebuddy.utility.JavaModule;

/**
 * The ByteBuddy re/transformation manager.
 *
 * @author Seva Safris
 */
public class ByteBuddyManager extends Manager {
  private static final Logger logger = Logger.getLogger(ByteBuddyManager.class);
  private static final ByteBuddy byteBuddy = new ByteBuddy().with(TypeValidation.DISABLED);

  private static final AgentBuilder.LocationStrategy bootFallbackLocationStrategy = new AgentBuilder.LocationStrategy() {
    @Override
    public ClassFileLocator classFileLocator(final ClassLoader classLoader, final JavaModule module) {
      return new ClassFileLocator.Compound(ClassFileLocator.ForClassLoader.of(classLoader), ClassFileLocator.ForClassLoader.ofBootLoader());
    }
  };

  private static AgentBuilder newBuilder() {
    // Prepare the builder to be used to implement transformations in AgentRule(s)
    AgentBuilder agentBuilder = new Default(byteBuddy);
    if (AgentRuleUtil.tracerClassLoader != null)
      agentBuilder = agentBuilder.ignore(any(), is(AgentRuleUtil.tracerClassLoader));

    return agentBuilder
      .ignore(nameStartsWith("net.bytebuddy.").or(nameStartsWith("sun.reflect.")).or(isSynthetic()), any(), any())
      .disableClassFormatChanges()
      .with(RedefinitionStrategy.RETRANSFORMATION)
      .with(InitializationStrategy.NoOp.INSTANCE)
      .with(TypeStrategy.Default.REDEFINE)
      .with(bootFallbackLocationStrategy);
  }

  private static void log(final Level level, final String message, final Throwable t) {
    if (t instanceof IncompatiblePluginException || t instanceof IllegalStateException && t.getMessage().startsWith("Cannot resolve type description for "))
      logger.log(level, message + "\n" + t.getClass().getName() + ": " + t.getMessage());
    else
      logger.log(level, message, t);
  }

  private static void log(final Level level, final String message) {
    logger.log(level, message);
  }

  private static void assertParent(final AgentBuilder expected, final AgentBuilder builder) {
    try {
      final Class<?> cls = Class.forName("net.bytebuddy.agent.builder.AgentBuilder$Default$Transforming");
      final Field field = cls.getDeclaredField("this$0");
      field.setAccessible(true);
      for (AgentBuilder parent = builder; (parent = (AgentBuilder)field.get(parent)) != null;)
        if (parent == expected)
          return;

      throw new IllegalArgumentException("AgentBuilder instance provided by AgentRule#buildAgent(AgentBuilder) was not used");
    }
    catch (final ClassNotFoundException | IllegalAccessException | NoSuchFieldException e) {
      throw new IllegalStateException(e);
    }
  }

  ByteBuddyManager() {
    super("otarules.mf");
  }

  private final Set<String> loadedRules = new HashSet<>();

  @Override
  LinkedHashMap<AgentRule,Integer> scanRules(final Instrumentation inst, final LinkedHashMap<AgentRule,Integer> agentRules, final ClassLoader allRulesClassLoader, final Map<File,Integer> ruleJarToIndex, final Map<String,String> classNameToName, final PluginManifest.Directory pluginManifestDirectory) throws IOException {
    LinkedHashMap<AgentRule,Integer> deferrers = null;
    AgentRule agentRule = null;
    try {
      // Prepare the agent rules
      final Enumeration<URL> enumeration = allRulesClassLoader.getResources(file);
      while (enumeration.hasMoreElements()) {
        final URL scriptUrl = enumeration.nextElement();
        final File ruleJar = SpecialAgentUtil.getSourceLocation(scriptUrl, file);
        if (logger.isLoggable(Level.FINEST))
          logger.finest("Dereferencing index for " + ruleJar);

        final int index = ruleJarToIndex.get(ruleJar);
        try (final BufferedReader reader = new BufferedReader(new InputStreamReader(scriptUrl.openStream()))) {
          for (String line; (line = reader.readLine()) != null;) {
            line = line.trim();
            if (line.length() == 0 || line.charAt(0) == '#')
              continue;

            if (loadedRules.contains(line)) {
              if (logger.isLoggable(Level.FINE))
                logger.fine("Skipping loaded rule: " + line);

              continue;
            }

            final Class<?> agentClass = Class.forName(line, true, allRulesClassLoader);
            if (!AgentRule.class.isAssignableFrom(agentClass)) {
              logger.severe("Class " + agentClass.getName() + " does not implement " + AgentRule.class);
              continue;
            }

            final PluginManifest pluginManifest = pluginManifestDirectory.get(ruleJar);
            final String simpleClassName = line.substring(line.lastIndexOf('.') + 1);
            if (AssembleUtil.isSystemProperty("sa.instrumentation.plugin." + pluginManifest.name + "#" + simpleClassName + ".disable")) {
              if (logger.isLoggable(Level.FINE))
                logger.fine("Skipping disabled rule: " + line);

              continue;
            }

            if (AgentRule.class.isAssignableFrom(agentClass)) {
              if (logger.isLoggable(Level.FINE))
                logger.fine("Installing new rule: " + line);

              classNameToName.put(agentClass.getName(), pluginManifest.name);
              agentRule = (AgentRule)agentClass.getConstructor().newInstance();
              if (agentRule.isDeferrable(inst)) {
                if (deferrers == null)
                  deferrers = new LinkedHashMap<>(1);

                deferrers.put(agentRule, index);
              }
              else {
                agentRules.put(agentRule, index);
              }
            }
          }
        }
      }
    }
    catch (final UnsupportedClassVersionError | InvocationTargetException e) {
      logger.log(Level.SEVERE, "Error initliaizing rule: " + agentRule, e);
    }
    catch (final InstantiationException e) {
      logger.log(Level.SEVERE, "Unable to instantiate: " + agentRule, e);
    }
    catch (final ClassNotFoundException | IllegalAccessException | NoSuchMethodException e) {
      throw new IllegalStateException(e);
    }

    return deferrers;
  }

  private boolean loadedDefaultRules;

  private void loadDefaultRules(final Instrumentation inst, final Event[] events) {
    if (loadedDefaultRules)
      return;

    loadedDefaultRules = true;

    // Load ClassLoaderAgentRule
    loadAgentRule(inst, new ClassLoaderAgentRule(), newBuilder(), -1, events);

    // Load MutexAgentRule
    loadAgentRule(inst, new MutexAgentRule(), newBuilder(), -1, events);
  }

  @Override
  void loadRules(final Instrumentation inst, final Map<AgentRule,Integer> agentRules, final Event[] events) {
    // Ensure default rules are loaded
    loadDefaultRules(inst, events);

    // Load the rest of the specified rules
    if (agentRules != null) {
      for (final Map.Entry<AgentRule,Integer> entry : agentRules.entrySet()) {
        final AgentRule agentRule = entry.getKey();
        loadAgentRule(inst, agentRule, newBuilder(), entry.getValue(), events);
        loadedRules.add(agentRule.getClass().getName());
      }
    }
  }

  private void loadAgentRule(final Instrumentation inst, final AgentRule agentRule, final AgentBuilder agentBuilder, final int index, final Event[] events) {
    try {
      final Iterable<? extends AgentBuilder> builders = agentRule.buildAgent(agentBuilder);
      if (builders != null) {
        for (final AgentBuilder builder : builders) {
//          assertParent(agentBuilder, builder);
          builder.with(new TransformationListener(inst, index, events)).installOn(inst);
        }
      }
    }
    catch (final Exception e) {
      logger.log(Level.SEVERE, "Error invoking " + agentRule.getClass().getName() + "#buildAgent(AgentBuilder)", e);
    }
  }

  class TransformationListener implements Listener {
    private final Instrumentation inst;
    private final int index;
    private final Event[] events;

    TransformationListener(final Instrumentation inst, final int index, final Event[] events) {
      this.inst = inst;
      this.index = index;
      this.events = events;
    }

    @Override
    public void onDiscovery(final String typeName, final ClassLoader classLoader, final JavaModule module, final boolean loaded) {
      if (events[Event.DISCOVERY.ordinal()] != null)
        log(Level.SEVERE, "Event::onDiscovery(" + typeName + ", " + AssembleUtil.getNameId(classLoader) + ", " + module + ", " + loaded + ")");
    }

    @Override
    public void onTransformation(final TypeDescription typeDescription, final ClassLoader classLoader, final JavaModule module, final boolean loaded, final DynamicType dynamicType) {
      if (events[Event.TRANSFORMATION.ordinal()] != null)
        log(Level.SEVERE, "Event::onTransformation(" + typeDescription.getName() + ", " + AssembleUtil.getNameId(classLoader) + ", " + module + ", " + loaded + ", " + dynamicType + ")");

      if (index != -1 && !SpecialAgent.linkRule(index, classLoader))
        throw new IncompatiblePluginException(typeDescription.getName());

      if (classLoader != null) {
        try {
          final JavaModule unnamedModule = JavaModule.of(ClassLoader.class.getMethod("getUnnamedModule").invoke(classLoader));
          if (!module.canRead(unnamedModule)) {
            module.modify(inst, Collections.singleton(unnamedModule), Collections.EMPTY_MAP, Collections.EMPTY_MAP, Collections.EMPTY_SET, Collections.EMPTY_MAP);
            if (logger.isLoggable(Level.FINEST))
              logger.finest("Added module reads: " + module + " -> " + unnamedModule);
          }
        }
        catch (final NoSuchMethodException e) {
        }
        catch (final Throwable t) {
          logger.log(Level.SEVERE, t.getMessage(), t);
        }
      }
    }

    @Override
    public void onIgnored(final TypeDescription typeDescription, final ClassLoader classLoader, final JavaModule module, final boolean loaded) {
      if (events[Event.IGNORED.ordinal()] != null)
        log(Level.SEVERE, "Event::onIgnored(" + typeDescription.getName() + ", " + AssembleUtil.getNameId(classLoader) + ", " + module + ", " + loaded + ")");
    }

    @Override
    public void onError(final String typeName, final ClassLoader classLoader, final JavaModule module, final boolean loaded, final Throwable throwable) {
      if (events[Event.ERROR.ordinal()] != null)
        log(Level.SEVERE, "Event::onError(" + typeName + ", " + AssembleUtil.getNameId(classLoader) + ", " + module + ", " + loaded + ")", throwable);
    }

    @Override
    public void onComplete(final String typeName, final ClassLoader classLoader, final JavaModule module, final boolean loaded) {
      if (events[Event.COMPLETE.ordinal()] != null)
        log(Level.SEVERE, "Event::onComplete(" + typeName + ", " + AssembleUtil.getNameId(classLoader) + ", " + module + ", " + loaded + ")");
    }
  }
}