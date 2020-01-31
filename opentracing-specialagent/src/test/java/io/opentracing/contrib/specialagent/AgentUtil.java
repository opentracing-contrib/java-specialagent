/* Copyright 2019 The OpenTracing Authors
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
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Utility functions for the {@code AgentRunner}. This class was created as an
 * indirection from {@code AgentRunner} in order to solve class loading problems
 * between the loading of {@code AgentRunner} and the invocation of
 * {@link Instrumentation#appendToBootstrapClassLoaderSearch(JarFile)} in the
 * static block of {@code AgentRunner}.
 *
 * @author Seva Safris
 */
class AgentUtil {
  private static final Predicate<String> predicate = new Predicate<String>() {
    @Override
    public boolean test(final String t) {
      return t.endsWith(".class") && !t.contains("junit");
    }
  };

  /**
   * Returns a set of all ".class" file contained in the list of specified
   * paths, as well as subpaths of the paths. This method excludes classes
   * belonging to JUnit.
   *
   * @param files The paths.
   * @return A set of all files contained in the list of specified paths, as
   *         well as subpaths of the paths.
   * @throws IOException If an I/O error has occurred.
   */
  static Set<String> getClassFiles(final List<File> files) throws IOException {
    final Set<String> classFiles = new HashSet<>();
    for (final File file : files) {
      if (file.getName().endsWith(".jar")) {
        try (final JarFile jarFile = new JarFile(file)) {
          final Enumeration<JarEntry> entries = jarFile.entries();
          while (entries.hasMoreElements()) {
            final JarEntry entry = entries.nextElement();
            if (predicate.test(entry.getName()))
              classFiles.add(entry.getName());
          }
        }
      }
      else {
        final Path filePath = file.toPath();
        AssembleUtil.recurseDir(file, new Predicate<File>() {
          @Override
          public boolean test(final File t) {
            final String name = filePath.relativize(t.toPath()).toString().replace('\\', '/');
            if (predicate.test(name))
              classFiles.add(name);

            return true;
          }
        });
      }
    }

    return classFiles;
  }
}