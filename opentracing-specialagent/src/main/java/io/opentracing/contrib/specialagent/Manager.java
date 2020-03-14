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

import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.net.URL;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * An abstract re/transformation manager.
 *
 * @author Seva Safris
 */
public abstract class Manager {
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

  /**
   * Scans the rules from {@code agentRules}, prepares the provided arguments to
   * be used in a subsequent calls to
   * {@link #loadRules(Instrumentation,Map,Event[])}, and returns a map of
   * {@link AgentRule} that are identified to be deferrers via
   * {@link AgentRule#isDeferrable(Instrumentation)}.
   *
   * @param inst The {@code Instrumentation} instance.
   * @param agentRules The {@link LinkedHashMap} of {@link AgentRule}-to-index
   *          entries to be filled with rules to be later loaded in
   *          {@link #loadRules(Instrumentation,Map,Event[])}.
   * @param pluginsClassLoader The {@code ClassLoader} having a classpath with
   *          all rule JARs.
   * @param pluginManifestDirectory Map between a JAR file and the associated
   *          {@link PluginManifest}.
   * @param ruleJarToIndex A {@link Map} of rule JAR path to its index in the
   *          {@code allRulesClassLoader} classpath to be filled by this method.
   * @param classNameToName A {@link Map} of class names to plugin names to be
   *          filled by this method.
   * @return A {@link LinkedHashMap} of {@link AgentRule}-to-index entries with
   *         deferrers to be loaded in
   *         {@link #loadRules(Instrumentation,Map,Event[])}.
   * @throws IOException If an I/O error has occurred.
   */
  abstract Map<AgentRule,PluginManifest> scanRules(Instrumentation inst, ClassLoader pluginsClassLoader, PluginManifest.Directory pluginManifestDirectory, Map<AgentRule,PluginManifest> pluginManifests, Map<String,String> classNameToName) throws IOException;

  /**
   * Loads the rules of this {@code Manager} and associates relevant state in
   * the specified arguments.
   *
   * @param inst The {@code Instrumentation} instance.
   * @param agentRules The {@link LinkedHashMap} of {@link AgentRule}-to-index
   *          entries filled with rules to be loaded.
   * @param events Manager events to log.
   */
  abstract void loadRules(Instrumentation inst, Map<AgentRule,PluginManifest> pluginManifests, Event[] events);
}