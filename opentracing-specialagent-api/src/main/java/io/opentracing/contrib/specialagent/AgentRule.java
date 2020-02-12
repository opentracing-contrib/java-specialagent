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
import java.util.Map;

import net.bytebuddy.agent.builder.AgentBuilder;

/**
 * Base abstract class for SpecialAgent Instrumentation Rules.
 *
 * @author Seva Safris
 */
public abstract class AgentRule {
  public static class $Access {
    private static Runnable initializer;

    /**
     * Load the {@link AgentRule} class and initialize
     * {@link AgentRule#isThreadInstrumentable}.
     * <p>
     * <b>Note:</b> This method must be called before tracer classes are loaded,
     * in order to capture lineage of threads started by the tracer.
     */
    static void load() {
      // "main" thread is instrumentable
      isThreadInstrumentable.set(Boolean.TRUE);
    }

    /**
     * Configures the {@link AgentRule} for initialization with the specified
     * {@code initializer} and {@code classNameToName} map.
     *
     * @param initializer The initializer {@link Runnable} for initializing
     *          {@link AgentRule} instances.
     * @param classNameToName A {@link Map} of class names to plugin names.
     */
    static void configure(final Runnable initializer, final Map<String,String> classNameToName) {
      $Access.initializer = initializer;
      AgentRule.classNameToName = classNameToName;
    }

    /**
     * Run the {@link $Access#initializer} {@link Runnable} previously set via
     * {@link $Access#configure(Runnable,Map)}.
     *
     * @return Whether an {@link $Access#initializer} was run.
     */
    public static boolean init() {
      if (initialized)
        return false;

      initialized = true;
      if (logger.isLoggable(Level.FINE))
        logger.fine("AgentRule.$Access.init(): initializer " + (initializer == null ? "=" : "!") + "= null");

      if (initializer == null)
        return false;

      initializer.run();
      initializer = null;
      return true;
    }

    /**
     * @return The {@link MutexLatch} instance from {@link AgentRule}.
     */
    static MutexLatch mutexLatch() {
      return mutexLatch;
    }
  }

  private static boolean initialized;

  private static final InheritableThreadLocal<Boolean> isThreadInstrumentable = new InheritableThreadLocal<Boolean>() {
    @Override
    protected Boolean childValue(Boolean parentValue) {
      if (parentValue == null) {
        logger.warning("Unknown instrumentable state for parent of thread: " + Thread.currentThread().getName());
        parentValue = Boolean.TRUE;
      }

      if (!parentValue || AgentRuleUtil.tracerClassLoader == null)
        return parentValue;

      return !AgentRuleUtil.isFromClassLoader(AgentRuleUtil.getExecutionStack(), AgentRuleUtil.tracerClassLoader);
    }

    @Override
    public Boolean get() {
      Boolean state = super.get();
      if (state == null) {
        logger.warning("Unknown instrumentable state for thread: " + Thread.currentThread().getName());
        set(state = Boolean.TRUE);
      }

      return state;
    }
  };

  private static final Logger logger = Logger.getLogger(AgentRule.class);
  private static final MutexLatch mutexLatch = new MutexLatch();
  private static Map<String,String> classNameToName;

  public static boolean isEnabled(final String agentRuleClass, final String origin) {
    final boolean enabled = initialized && mutexLatch.get() == 0 && isThreadInstrumentable.get();

    if (enabled) {
      if (logger.isLoggable(Level.FINER))
        logger.finer("-------> Intercept [" + agentRuleClass + "@" + Thread.currentThread().getName() + "]: " + origin);
    }
    else if (logger.isLoggable(Level.FINEST)) {
      logger.finest("-------> Intercept [" + agentRuleClass + "@" + Thread.currentThread().getName() + "] DROP: " + origin);
    }

    return enabled;
  }

  public static boolean isVerbose(final Class<? extends AgentRule> agentRuleClass) {
    final boolean pluginsVerbose = AssembleUtil.isSystemProperty("sa.instrumentation.plugin.*.verbose");
    if (pluginsVerbose)
      return pluginsVerbose;

    final String pluginName = classNameToName.get(agentRuleClass.getName());
    if (pluginName == null)
      throw new IllegalStateException("Plugin name must not be null");

    return AssembleUtil.isSystemProperty("sa.instrumentation.plugin." + pluginName + ".verbose");
  }

  /**
   * @param inst The {@code Instrumentation}.
   * @return If this method returns {@code true} for any enabled
   *         {@link AgentRule}s, the SpecialAgent will delegate the loading of {@link AgentRule} instances
   *         of the {@code loader} {@link Runnable}, which will be invoked by the first deferrable {@link AgentRule}
   *         that triggers the deferred loading. If this method returns
   *         {@code false} for all {@link AgentRule}s, the SpecialAgent will
   *         invoke {@code init} immediately.
   */
  public boolean isDeferrable(final Instrumentation inst) {
    return false;
  }

  public abstract Iterable<? extends AgentBuilder> buildAgent(AgentBuilder builder) throws Exception;
}