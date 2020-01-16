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
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
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
  private static final String RULES_FILE = "otarules.mf";

  private static final AgentBuilder.LocationStrategy bootFallbackLocationStrategy  = new AgentBuilder.LocationStrategy() {
    @Override
    public ClassFileLocator classFileLocator(final ClassLoader classLoader, final JavaModule module) {
      return new ClassFileLocator.Compound(ClassFileLocator.ForClassLoader.of(classLoader), ClassFileLocator.ForClassLoader.ofBootLoader());
    }
  };

  private static AgentBuilder newBuilder() {
    // Prepare the builder to be used to implement transformations in AgentRule(s)
    AgentBuilder agentBuilder = new Default(new ByteBuddy().with(TypeValidation.DISABLED))
      .disableClassFormatChanges()
      .ignore(none());

    if (AgentRuleUtil.tracerClassLoader != null)
      agentBuilder = agentBuilder.ignore(any(), is(AgentRuleUtil.tracerClassLoader));

    return agentBuilder
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
    super(RULES_FILE);
  }

  private final Set<String> loadedRules = new HashSet<>();

  @Override
  LinkedHashMap<AgentRule,Integer> initRules(final LinkedHashMap<AgentRule,Integer> agentRules, final ClassLoader allRulesClassLoader, final Map<File,Integer> ruleJarToIndex, final PluginManifest.Directory pluginManifestDirectory) throws IOException {
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

            System.err.println(line);
            final Class<?> agentClass = Class.forName(line, true, allRulesClassLoader);
            if (!AgentRule.class.isAssignableFrom(agentClass)) {
              logger.severe("Class " + agentClass.getName() + " does not implement " + AgentRule.class);
              continue;
            }

            final PluginManifest pluginManifest = pluginManifestDirectory.get(ruleJar);
            final String simpleClassName = line.substring(line.lastIndexOf('.') + 1);
            final String disableRuleClass = System.getProperty("sa.instrumentation.plugin." + pluginManifest.name + "#" + simpleClassName + ".disable");
            if (disableRuleClass != null && !"false".equals(disableRuleClass)) {
              if (logger.isLoggable(Level.FINE))
                logger.fine("Skipping disabled rule: " + line);

              continue;
            }

            if (AgentRule.class.isAssignableFrom(agentClass)) {
              if (logger.isLoggable(Level.FINE))
                logger.fine("Installing new rule: " + line);

              agentRule = (AgentRule)agentClass.getConstructor().newInstance();
              if (agentRule.isDeferrable(inst)) {
                if (deferrers == null)
                  deferrers = new LinkedHashMap<>(1);

                deferrers.put(agentRule, index);
              }
              else {
                AgentRule.classNameToName.put(agentClass.getName(), pluginManifest.name);
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

  @Override
  void loadRules(final Map<AgentRule,Integer> agentRules, final Event[] events) {
    AgentRule agentRule = null;
    try {
      // Load ClassLoader Agent
      agentRule = new ClassLoaderAgentRule();
      loadAgentRule(agentRule, newBuilder(), -1, events);

      // Load the Mutex Agent
      MutexAgent.premain(inst);

      for (final Map.Entry<AgentRule,Integer> entry : agentRules.entrySet()) {
        agentRule = entry.getKey();
        loadAgentRule(agentRule, newBuilder(), entry.getValue(), events);
        loadedRules.add(agentRule.getClass().getName());
      }
    }
    catch (final Exception e) {
      logger.log(Level.SEVERE, "Error invoking " + agentRule + "#buildAgent(AgentBuilder) was not found", e);
    }
  }

  private void loadAgentRule(final AgentRule agentRule, final AgentBuilder agentBuilder, final int index, final Event[] events) throws Exception {
    final Iterable<? extends AgentBuilder> builders = agentRule.buildAgent(agentBuilder);
    if (builders != null) {
      for (final AgentBuilder builder : builders) {
//        assertParent(agentBuilder, builder);
        builder.with(new TransformationListener(index, events)).installOn(inst);
      }
    }
  }

  class TransformationListener implements Listener {
    private final int index;
    private final Event[] events;

    TransformationListener(final int index, final Event[] events) {
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
        catch (final IllegalAccessException | InvocationTargetException e) {
          throw new IllegalStateException(e);
        }
        catch (final NoSuchMethodException e) {
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