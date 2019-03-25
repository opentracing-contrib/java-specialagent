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
  public enum Event {
    COMPLETE,
    DISCOVERY,
    ERROR,
    IGNORED,
    TRANSFORMATION;
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
   * @param allRulesClassLoader The {@code ClassLoader} having a classpath
   *          with all rule JARs.
   * @param ruleJarToIndex A {@code Map} of rule JAR path to its index in the
   *          {@code allRulesClassLoader} classpath.
   * @param agentArgs The agent arguments.
   * @param events Manager events to log.
   * @throws IOException If an I/O error has occurred.
   */
  abstract void loadRules(ClassLoader allRulesClassLoader, Map<URL,Integer> ruleJarToIndex, String agentArgs, Event[] events) throws IOException;
}