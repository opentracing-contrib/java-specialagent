/* Copyright 2018 The OpenTracing Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentracing.contrib.specialagent;

import java.lang.instrument.Instrumentation;
import java.util.HashMap;
import java.util.Map;

import net.bytebuddy.agent.builder.AgentBuilder;

/**
 * Base abstract class for SpecialAgent Instrumentation Rules.
 *
 * @author Seva Safris
 */
public abstract class AgentRule {
  private static final Logger logger = Logger.getLogger(AgentRule.class);

  static final Map<String,String> classNameToName = new HashMap<>();
  static volatile boolean initialized = false;

  static Runnable init;

  /**
   * Initialize all {@link AgentRule}s. This method is called in either Static
   * Attach mode by SpecialAgent, or in Static Deferred Attach mode by
   * implementers of the {@link DeferredAttach} interface.
   *
   * @return Whether initialization was run.
   */
  public static boolean initialize() {
    if (init == null)
      return false;

    init.run();
    init = null;
    return true;
  }

  public static final ThreadLocal<Integer> latch = new ThreadLocal<Integer>() {
    @Override
    protected Integer initialValue() {
      return 0;
    }
  };

  public static boolean isEnabled(final String origin) {
    final boolean enabled = initialized && latch.get() == 0;
    if (enabled) {
      if (logger.isLoggable(Level.FINER))
        logger.finer("-------> Intercept from: " + origin);
    }
    else if (logger.isLoggable(Level.FINEST)) {
      logger.finest("-------> Intercept DROP: " + origin);
    }

    return enabled;
  }

  public static boolean isVerbose(final Class<? extends AgentRule> agentRuleClass) {
    final String pluginsVerboseProperty = System.getProperty("sa.instrumentation.plugin.*.verbose");
    final boolean pluginsVerbose = pluginsVerboseProperty != null && !"false".equals(pluginsVerboseProperty);

    final String pluginName = classNameToName.get(agentRuleClass.getName());
    if (pluginName == null)
      throw new IllegalStateException("Plugin name should not be null");

    final String pluginVerboseProperty = System.getProperty("sa.instrumentation.plugin." + pluginName + ".verbose");
    final boolean pluginVerbose = pluginVerboseProperty != null && !"false".equals(pluginVerboseProperty);
    return pluginsVerbose || pluginVerbose;
  }

  /**
   * @param inst The {@code Instrumentation}.
   * @return If this method returns {@code true} for any enabled
   *         {@link AgentRule}s, the SpecialAgent will delegate the invocation
   *         of the {@code init} {@link Runnable} to the first {@link AgentRule}
   *         that triggers the deferred initialization. If this method returns
   *         {@code false} for all {@link AgentRule}s, the SpecialAgent will
   *         invoke {@code init} immediately.
   */
  public boolean isDeferrable(final Instrumentation inst) {
    return false;
  }

  public abstract Iterable<? extends AgentBuilder> buildAgent(AgentBuilder builder) throws Exception;
}