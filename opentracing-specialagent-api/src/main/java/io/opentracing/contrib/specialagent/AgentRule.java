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
  static boolean initialized = false;

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
    final String pluginsVerboseProperty = System.getProperty("sa.instrumentation.plugins.verbose");
    final boolean pluginsVerbose = pluginsVerboseProperty != null && !"false".equals(pluginsVerboseProperty);

    final String pluginName = classNameToName.get(agentRuleClass.getName());
    if (pluginName == null)
      throw new IllegalStateException("Plugin name should not be null");

    final String pluginVerboseProperty = System.getProperty("sa.instrumentation.plugin." + pluginName + ".verbose");
    final boolean pluginVerbose = pluginVerboseProperty != null && !"false".equals(pluginVerboseProperty);
    return pluginsVerbose || pluginVerbose;
  }

  public abstract Iterable<? extends AgentBuilder> buildAgent(AgentBuilder builder) throws Exception;
}