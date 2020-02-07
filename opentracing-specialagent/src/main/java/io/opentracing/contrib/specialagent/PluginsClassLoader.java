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
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Set;

/**
 * {@code ClassLoader} to contain all Instrumentation and Tracer Plugin(s) that
 * are packaged inside SpecialAgent.
 *
 * @author Seva Safris
 */
class PluginsClassLoader extends URLClassLoader {
  private final Set<File> set;
  private final File[] array;

  /**
   * Creates a new {@code PluginsClassLoader} with the specified set of files
   * providing the JAR paths.
   *
   * @param files The {@code File} objects providing the JAR paths.
   */
  public PluginsClassLoader(final Set<File> files) {
    // Override parent ClassLoader methods to avoid delegation of resource
    // resolution to bootstrap class loader
    super(AssembleUtil.toURLs(files), new ClassLoader(null) {
      // Overridden to ensure resources are not discovered in bootstrap class loader
      @Override
      public Enumeration<URL> getResources(final String name) throws IOException {
        return null;
      }
    });
    this.set = files;
    this.array = files.toArray(new File[files.size()]);
  }

  /**
   * @return The array of {@code File} objects providing the JAR paths in this
   *         {@code PluginsClassLoader}.
   */
  public File[] getFiles() {
    return array;
  }

  /**
   * Tests whether the specified file is present in this
   * {@code PluginsClassLoader}.
   *
   * @param file The {@code File} to test.
   * @return Whether the specified file is present in this
   *         {@code PluginsClassLoader}.
   */
  public boolean containsPath(final File file) {
    return set.contains(file);
  }

  @Override
  public String toString() {
    return Arrays.toString(array);
  }
}