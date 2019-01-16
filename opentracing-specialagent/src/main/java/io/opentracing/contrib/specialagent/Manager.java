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
   * Execute the {@code premain} instructions.
   *
   * @param agentArgs The agent arguments.
   * @param instrumentation The {@link Instrumentation}.
   * @throws Exception If an error has occurred.
   */
  abstract void premain(String agentArgs, Instrumentation instrumentation) throws Exception;

  /**
   * Loads the rules of this {@code Manager} and associates relevant state in
   * the specified arguments.
   *
   * @param allPluginsClassLoader The {@code ClassLoader} having a classpath
   *          with all plugin JARs.
   * @param pluginJarToIndex A {@code Map} of plugin JAR path to its index in
   *          the {@code allPluginsClassLoader} classpath.
   * @param agentArgs The agent arguments.
   * @throws IOException If an I/O error has occurred.
   */
  abstract void loadRules(ClassLoader allPluginsClassLoader, Map<String,Integer> pluginJarToIndex, String agentArgs) throws IOException;

  /**
   * Disables triggering of rules inside the current thread.
   *
   * @return {@code true} if triggering was previously enabled, and
   *         {@code false} if it was already disabled.
   */
  abstract boolean disableTriggers();

  /**
   * Enables triggering of rules inside the current thread.
   *
   * @return {@code true} if triggering was previously disabled, and
   *         {@code false} if it was already enabled.
   */
  abstract boolean enableTriggers();
}