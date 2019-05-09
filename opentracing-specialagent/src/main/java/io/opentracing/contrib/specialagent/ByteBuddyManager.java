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
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.util.Enumeration;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.agent.builder.AgentBuilder.Identified.Narrowable;
import net.bytebuddy.agent.builder.AgentBuilder.InitializationStrategy;
import net.bytebuddy.agent.builder.AgentBuilder.Listener;
import net.bytebuddy.agent.builder.AgentBuilder.RedefinitionStrategy;
import net.bytebuddy.agent.builder.AgentBuilder.Transformer;
import net.bytebuddy.agent.builder.AgentBuilder.TypeStrategy;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.DynamicType.Builder;
import net.bytebuddy.utility.JavaModule;

/**
 * The ByteBuddy re/transformation manager.
 *
 * @author Seva Safris
 */
public class ByteBuddyManager extends Manager {
  private static final Logger logger = Logger.getLogger(ByteBuddyManager.class.getName());
  private static final String RULES_FILE = "otarules.mf";

  private static void installOn(final Narrowable builder, final Class<?> advice, final AgentRule agentRule, final Listener listener, final Instrumentation instrumentation) {
    builder
      .transform(new Transformer() {
        @Override
        public Builder<?> transform(final Builder<?> builder, final TypeDescription typeDescription, final ClassLoader classLoader, final JavaModule module) {
          return null;//builder.visit(Advice.to(advice).on(agentRule.onMethod()));
        }
      })
      .with(listener)
      .installOn(instrumentation);
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

  private static AgentBuilder newBuilder() {
    // Prepare the builder to be used to implement transformations in AgentRule(s)
    AgentBuilder agentBuilder = new AgentBuilder.Default().ignore(none());
    if (AgentRuleUtil.tracerClassLoader != null)
      agentBuilder = agentBuilder.ignore(any(), is(AgentRuleUtil.tracerClassLoader));

    return agentBuilder
      .with(RedefinitionStrategy.RETRANSFORMATION)
      .with(InitializationStrategy.NoOp.INSTANCE)
      .with(TypeStrategy.Default.REDEFINE);
  }

  private Instrumentation inst;

  ByteBuddyManager() {
    super(RULES_FILE);
  }

  @Override
  void premain(final String agentArgs, final Instrumentation inst) throws Exception {
    this.inst = inst;
    SpecialAgent.initialize();
  }

  @Override
  void loadRules(final ClassLoader allRulesClassLoader, final Map<URL,Integer> ruleJarToIndex, final Map<String,String> nameToVersion, final Event[] events) throws IOException {
    AgentRule agentRule = null;
    try {
      // Load ClassLoader Agent
      agentRule = new ClassLoaderAgentRule();
      loadAgentRule(agentRule, newBuilder(), -1, events);

      // Load the Mutex Agent
      MutexAgent.premain(inst);

      // Prepare the agent rules
      final Enumeration<URL> enumeration = allRulesClassLoader.getResources(file);
      while (enumeration.hasMoreElements()) {
        final URL scriptUrl = enumeration.nextElement();
        final URL ruleJar = SpecialAgentUtil.getSourceLocation(scriptUrl, file);
        if (logger.isLoggable(Level.FINEST))
          logger.finest("Dereferencing index for " + ruleJar);

        final int index = ruleJarToIndex.get(ruleJar);

        final BufferedReader reader = new BufferedReader(new InputStreamReader(scriptUrl.openStream()));
        for (String line; (line = reader.readLine()) != null;) {
          line = line.trim();
          if (line.length() == 0 || line.charAt(0) == '#')
            continue;

          if (logger.isLoggable(Level.FINE))
            logger.fine("Installing rule: " + line);

          final Class<?> agentClass = Class.forName(line, true, allRulesClassLoader);
          if (!AgentRule.class.isAssignableFrom(agentClass)) {
            logger.severe("Class " + agentClass.getName() + " does not implement " + AgentRule.class);
            continue;
          }

          final String jarName = AgentRuleUtil.getPluginName(ruleJar);
          final String version = nameToVersion.get(jarName);
          final String name = jarName.endsWith(version) ? jarName.substring(0, jarName.length() - version.length() - 1) : jarName;

          AgentRule.classNameToName.put(agentClass.getName(), name);

          agentRule = (AgentRule)agentClass.getConstructor().newInstance();
          loadAgentRule(agentRule, newBuilder(), index, events);
        }
      }
    }
    catch (final UnsupportedClassVersionError | InvocationTargetException e) {
      logger.log(Level.SEVERE, "Error initliaizing rule: " + agentRule, e);
    }
    catch (final InstantiationException e) {
      logger.log(Level.SEVERE, "Unable to instantiate: " + agentRule, e);
    }
    catch (final ClassNotFoundException | IllegalAccessException e) {
      throw new IllegalStateException(e);
    }
    catch (final Exception e) {
      logger.log(Level.SEVERE, "Error invoking " + agentRule + "#buildAgent(AgentBuilder) was not found", e);
    }
  }

  private void loadAgentRule(final AgentRule agentRule, final AgentBuilder agentBuilder, final int index, final Event[] events) throws Exception {
    final Iterable<? extends AgentBuilder> builders = agentRule.buildAgent(agentBuilder);
    for (final AgentBuilder builder : builders) {
//      assertParent(agentBuilder, builder);
      final TransformationListener listener = new TransformationListener(index, events);
//      if (agentRule.onEn().getOnEnter() != null)
//        installOn(builder, agentRule.onEn().getOnEnter(), agentRule, listener, instrumentation);
//
//      if (agentRule.onEn().getOnExit() != null)
//        installOn(builder, agentRule.onEn().getOnExit(), agentRule, listener, instrumentation);

      builder.with(listener).installOn(inst);
    }
  }

  class TransformationListener implements AgentBuilder.Listener {
    private final int index;
    private final Event[] events;

    TransformationListener(final int index, final Event[] events) {
      this.index = index;
      this.events = events;
    }

    @Override
    public void onDiscovery(final String typeName, final ClassLoader classLoader, final JavaModule module, final boolean loaded) {
      if (events[Event.DISCOVERY.ordinal()] != null)
        logger.severe("Event::onDiscovery(" + typeName + ", " + SpecialAgentUtil.getIdentityCode(classLoader) + ", " + module + ", " + loaded + ")");
    }

    @Override
    public void onTransformation(final TypeDescription typeDescription, final ClassLoader classLoader, final JavaModule module, final boolean loaded, final DynamicType dynamicType) {
      if (events[Event.TRANSFORMATION.ordinal()] != null)
        logger.severe("Event::onTransformation(" + typeDescription.getName() + ", " + SpecialAgentUtil.getIdentityCode(classLoader) + ", " + module + ", " + loaded + ", " + dynamicType + ")");

      if (index != -1 && !SpecialAgent.linkRule(index, classLoader))
        throw new IllegalStateException("Disallowing transformation due to incompatibility");
    }

    @Override
    public void onIgnored(final TypeDescription typeDescription, final ClassLoader classLoader, final JavaModule module, final boolean loaded) {
      if (events[Event.IGNORED.ordinal()] != null)
        logger.severe("Event::onIgnored(" + typeDescription.getName() + ", " + SpecialAgentUtil.getIdentityCode(classLoader) + ", " + module + ", " + loaded + ")");
    }

    @Override
    public void onError(final String typeName, final ClassLoader classLoader, final JavaModule module, final boolean loaded, final Throwable throwable) {
      if (events[Event.ERROR.ordinal()] != null)
        logger.log(Level.SEVERE, "Event::onError(" + typeName + ", " + SpecialAgentUtil.getIdentityCode(classLoader) + ", " + module + ", " + loaded + ")", throwable);
    }

    @Override
    public void onComplete(final String typeName, final ClassLoader classLoader, final JavaModule module, final boolean loaded) {
      if (events[Event.COMPLETE.ordinal()] != null)
        logger.severe("Event::onComplete(" + typeName + ", " + SpecialAgentUtil.getIdentityCode(classLoader) + ", " + module + ", " + loaded + ")");
    }
  }
}