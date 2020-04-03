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

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.instrument.Instrumentation;
import java.util.HashMap;
import java.util.Map;

import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;

/**
 * Base abstract class for SpecialAgent Integration Rules.
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
     * @return The {@link ThreadLocalCounter} instance from {@link AgentRule}.
     */
    static ThreadLocalCounter entryCounter() {
      return entryCounter;
    }

    /**
     * Set the provided {@link PluginManifest} for the specified
     * {@link AgentRule}.
     *
     * @param agentRule The {@link AgentRule} for which to set the specified
     *          {@link PluginManifest}.
     * @param pluginManifest The {@link PluginManifest} to set in the provided
     *          {@link AgentRule}.
     */
    public static void setPluginManifest(final AgentRule agentRule, final PluginManifest pluginManifest) {
      agentRule.pluginManifest = pluginManifest;
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

      if (!parentValue || Adapter.tracerClassLoader == null)
        return parentValue;

      return !AgentRuleUtil.isFromClassLoader(AgentRuleUtil.getExecutionStack(), Adapter.tracerClassLoader);
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
  private static final ThreadLocalCounter entryCounter = new ThreadLocalCounter();
  private static final ThreadLocal<String> currentAgentRuleClass = new ThreadLocal<>();
  private static Map<String,String> classNameToName;

  public static String getCurrentPluginName() {
    return classNameToName.get(currentAgentRuleClass.get());
  }

  public static boolean isVerbose(final String className) {
    final boolean integrationsVerbose = AssembleUtil.isSystemProperty("sa.integration.*.verbose", "sa.instrumentation.plugin.*.verbose");
    if (integrationsVerbose)
      return integrationsVerbose;

    final String integrationName = classNameToName.get(className);
    if (integrationName == null)
      throw new IllegalStateException("Plugin name must not be null");

    return AssembleUtil.isSystemProperty("sa.integration." + integrationName + ".verbose", "sa.instrumentation.plugin." + integrationName + ".verbose");
  }

  private final String className = getClass().getName();
  private PluginManifest pluginManifest;

  @Retention(RetentionPolicy.RUNTIME)
  public @interface ClassName {
  }

  public final Advice.WithCustomMapping advice(final TypeDescription typeDescription) {
    final PluginManifest pluginManifest = typeDescriptionToPluginManifest.get(typeDescription);
    if (pluginManifest != null && pluginManifest != this.pluginManifest)
      logger.severe("<><><><> Multiple integrations registered for: " + typeDescription);
    else
      typeDescriptionToPluginManifest.put(typeDescription, this.pluginManifest);

    return Advice.withCustomMapping().bind(ClassName.class, className);
  }

  public static boolean isAllowed(final String className, final String origin) {
    final boolean allowed = initialized && entryCounter.get() == 0 && isThreadInstrumentable.get();
    if (allowed) {
      if (logger.isLoggable(Level.FINER))
        logger.finer("-------> Intercept [" + className.substring(className.lastIndexOf('.') + 1) + "@" + Thread.currentThread().getName() + "]: " + origin);

      currentAgentRuleClass.set(className);
    }
    else if (logger.isLoggable(Level.FINEST)) {
      logger.finest("-------> Intercept [" + className.substring(className.lastIndexOf('.') + 1) + "@" + Thread.currentThread().getName() + "] DROP: " + origin);
    }

    return allowed;
  }

  private static Map<TypeDescription,PluginManifest> typeDescriptionToPluginManifest = new HashMap<>();

  public static PluginManifest getPluginManifest(final TypeDescription typeDescription) {
    return typeDescriptionToPluginManifest.get(typeDescription);
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

  /**
   * Callback method for the construction of {@link AgentBuilder} rules off of
   * the provided {@link AgentBuilder} instance.
   * <p>
   * The {@link AgentBuilder} instances returned by this method will be
   * installed independently, resulting in a re/transformation cycle for each
   * instance.
   * <p>
   * <i><b>Note</b>: The use of this callback will result in the <b>worst</b>
   * performance profile for the loading of declared rules.</i>
   * <p>
   * <i>This method <b>should only be used</b> if the rule(s) being created
   * involve a {@link TypeDescription} that is <b>not unique amongst all the
   * rules</b> (i.e. the same {@link TypeDescription} is declared in another
   * rule).</i>
   *
   * @param builder The {@link AgentBuilder} to be used as the seed instance for
   *          the returned {@link AgentBuilder} instances.
   * @return An array of {@link AgentBuilder} instances declaring
   *         re/transformation rules.
   */
  public AgentBuilder[] buildAgentUnchained(final AgentBuilder builder) {
    return null;
  }

  /**
   * Callback method for the construction of a single {@link AgentBuilder} rule
   * off of the provided {@link AgentBuilder} instance.
   * <p>
   * The {@link AgentBuilder} instance returned by this method will be installed
   * in a chain that is local to the scope of the module in which it is
   * declared. For instance, if a rule module has 3 classes extending
   * {@link AgentRule}, and each implements a
   * {@link #buildAgentChainedLocal1(AgentBuilder)} callback, then the rules
   * declared in all {@link AgentRule} subclasses for the
   * {@link #buildAgentChainedLocal1(AgentBuilder)} callback will be installed
   * in a single call, resulting in one re/transformation cycle per module.
   * <p>
   * <i><b>Note</b>: The use of this callback will result in the <b>second
   * best</b> performance profile for the loading of declared rules.</i>
   * <p>
   * <i>This method <b>cannot be used</b> if the rule(s) being created involve a
   * {@link TypeDescription} that is <b>not unique amongst all the rules</b>
   * (i.e. the same {@link TypeDescription} is declared in another rule).</i>
   *
   * @param builder The {@link AgentBuilder} to be used as the seed instance for
   *          the returned {@link AgentBuilder} instances.
   * @return A {@link AgentBuilder} instance declaring re/transformation rules.
   */
  public AgentBuilder buildAgentChainedLocal1(final AgentBuilder builder) {
    return null;
  }

  /**
   * Callback method for the construction of a single {@link AgentBuilder} rule
   * off of the provided {@link AgentBuilder} instance.
   * <p>
   * The {@link AgentBuilder} instance returned by this method will be installed
   * in a chain that is local to the scope of the module in which it is
   * declared. For instance, if a rule module has 3 classes extending
   * {@link AgentRule}, and each implements a
   * {@link #buildAgentChainedLocal2(AgentBuilder)} callback, then the rules
   * declared in all {@link AgentRule} subclasses for the
   * {@link #buildAgentChainedLocal2(AgentBuilder)} callback will be installed
   * in a single call, resulting in one re/transformation cycle per module.
   * <p>
   * <i><b>Note</b>: The use of this callback will result in the <b>second
   * best</b> performance profile for the loading of declared rules.</i>
   * <p>
   * <i>This method <b>cannot be used</b> if the rule(s) being created involve a
   * {@link TypeDescription} that is <b>not unique amongst all the rules</b>
   * (i.e. the same {@link TypeDescription} is declared in another rule).</i>
   *
   * @param builder The {@link AgentBuilder} to be used as the seed instance for
   *          the returned {@link AgentBuilder} instances.
   * @return A {@link AgentBuilder} instance declaring re/transformation rules.
   */
  public AgentBuilder buildAgentChainedLocal2(final AgentBuilder builder) {
    return null;
  }

  /**
   * Callback method for the construction of a single {@link AgentBuilder} rule
   * off of the provided {@link AgentBuilder} instance.
   * <p>
   * The {@link AgentBuilder} instance returned by this method will be installed
   * in a chain that is globally shared in the full scope of the VM. For
   * instance, for all rule modules that extend {@link AgentRule} and implement
   * a {@link #buildAgentChainedGlobal1(AgentBuilder)} callback, the rules
   * declared in all {@link AgentRule} subclasses for the
   * {@link #buildAgentChainedGlobal1(AgentBuilder)} callback will be installed
   * in a single call, resulting in one re/transformation cycle.
   * <p>
   * <i><b>Note</b>: The use of this callback will result in the <b>best</b>
   * performance profile for the loading of declared rules.</i>
   * <p>
   * <i>This method <b>cannot be used</b> if the rule(s) being created involve a
   * {@link TypeDescription} that is <b>not unique amongst all the rules</b>
   * (i.e. the same {@link TypeDescription} is declared in another rule).</i>
   *
   * @param builder The {@link AgentBuilder} to be used as the seed instance for
   *          the returned {@link AgentBuilder} instances.
   * @return A {@link AgentBuilder} instance declaring re/transformation rules.
   */
  public AgentBuilder buildAgentChainedGlobal1(final AgentBuilder builder) {
    return null;
  }

  /**
   * Callback method for the construction of a single {@link AgentBuilder} rule
   * off of the provided {@link AgentBuilder} instance.
   * <p>
   * The {@link AgentBuilder} instance returned by this method will be installed
   * in a chain that is globally shared in the full scope of the VM. For
   * instance, for all rule modules that extend {@link AgentRule} and implement
   * a {@link #buildAgentChainedGlobal2(AgentBuilder)} callback, the rules
   * declared in all {@link AgentRule} subclasses for the
   * {@link #buildAgentChainedGlobal2(AgentBuilder)} callback will be installed
   * in a single call, resulting in one re/transformation cycle.
   * <p>
   * <i><b>Note</b>: The use of this callback will result in the <b>best</b>
   * performance profile for the loading of declared rules.</i>
   * <p>
   * <i>This method <b>cannot be used</b> if the rule(s) being created involve a
   * {@link TypeDescription} that is <b>not unique amongst all the rules</b>
   * (i.e. the same {@link TypeDescription} is declared in another rule).</i>
   *
   * @param builder The {@link AgentBuilder} to be used as the seed instance for
   *          the returned {@link AgentBuilder} instances.
   * @return A {@link AgentBuilder} instance declaring re/transformation rules.
   */
  public AgentBuilder buildAgentChainedGlobal2(final AgentBuilder builder) {
    return null;
  }
}