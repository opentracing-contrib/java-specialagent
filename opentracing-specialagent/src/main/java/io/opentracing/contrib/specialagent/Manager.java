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

import java.io.File;
import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.net.URL;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * An abstract re/transformation manager.
 *
 * @author Seva Safris
 */
public abstract class Manager {
  public enum Event {
    COMPLETE,
    DISCOVERY,
    ERROR,
    IGNORED,
    TRANSFORMATION
  }

  /** The configuration file name. **/
  final String file;

  /**
   * Creates a new {@code Manager} with the specified configuration file name.
   *
   * @param file The configuration file.
   */
  Manager(final String file) {
    this.file = file;
  }

  /**
   * Returns the resources in the system class loader matching the configuration
   * file name from {@link #file}.
   *
   * @return The resources in the system class loader matching the configuration
   *         file name from {@link #file}.
   * @throws IOException If an I/O error has occurred.
   */
  final Enumeration<URL> getResources() throws IOException {
    return ClassLoader.getSystemClassLoader().getResources(file);
  }

  Instrumentation inst;

  /**
   * Execute the {@code premain} instructions.
   *
   * @param agentArgs The agent arguments.
   * @param inst The {@link Instrumentation}.
   */
  final void premain(final String agentArgs, final Instrumentation inst) {
    this.inst = inst;
    SpecialAgent.initialize(this);
  }

  /**
   * Initializes the rules of this {@code Manager} and calls
   * {@link DeferredAttach#isDeferrable(Instrumentation)} on all implementers of
   * the {@link DeferredAttach} interface to attempt to activate Static Deferred
   * Attach.
   *
   * @param agentRules The {@link LinkedHashMap} of {@link AgentRule}-to-index
   *          entries to be filled with rules to be later loaded in
   *          {@link #loadRules(Map,Event[])}.
   * @param allRulesClassLoader The {@code ClassLoader} having a classpath with
   *          all rule JARs.
   * @param ruleJarToIndex A {@code Map} of rule JAR path to its index in the
   *          {@code allRulesClassLoader} classpath.
   * @param pluginManifestDirectory Map between a JAR file and the associated
   *          {@link PluginManifest}.
   * @return A {@link LinkedHashMap} of {@link AgentRule}-to-index entries with
   *         deferrers to be loaded in {@link #loadRules(Map,Event[])}.
   * @throws IOException If an I/O error has occurred.
   */
  abstract LinkedHashMap<AgentRule,Integer> initRules(final LinkedHashMap<AgentRule,Integer> agentRules, final ClassLoader allRulesClassLoader, final Map<File,Integer> ruleJarToIndex, final PluginManifest.Directory pluginManifestDirectory) throws IOException;

  /**
   * Loads the rules of this {@code Manager} and associates relevant state in
   * the specified arguments.
   *
   * @param agentRules The {@link LinkedHashMap} of {@link AgentRule}-to-index
   *          entries filled with rules to be loaded.
   * @param events Manager events to log.
   */
  abstract void loadRules(final Map<AgentRule,Integer> agentRules, final Event[] events);
}