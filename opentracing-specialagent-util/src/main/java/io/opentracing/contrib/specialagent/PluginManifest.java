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
import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class PluginManifest {
  static enum Type {
    INSTRUMENTATION,
    TRACER
  }

  private static PluginManifest getPluginManifestFromEntry(final File path, final String entry) {
    if (entry.startsWith("sa.plugin.name."))
      return new PluginManifest(Type.INSTRUMENTATION, entry.substring(15));

    if ("META-INF/services/io.opentracing.contrib.tracerresolver.TracerFactory".equals(entry))
      return new PluginManifest(Type.TRACER, path.getName().substring(0, path.getName().length() - 4));

    return null;
  }

  public static PluginManifest getPluginManifest(final File file) {
    if (file.isDirectory()) {
      final PluginManifest[] pluginManifest = new PluginManifest[1];
      final Path path = file.toPath();
      AssembleUtil.recurseDir(file, new Function<File,FileVisitResult>() {
        @Override
        public FileVisitResult apply(final File t) {
          final String entry = path.relativize(t.toPath()).toString();
          if (entry.contains("/") && !entry.startsWith("META-INF"))
            return FileVisitResult.SKIP_SIBLINGS;

          pluginManifest[0] = getPluginManifestFromEntry(file, entry);
          return pluginManifest[0] != null ? FileVisitResult.TERMINATE : FileVisitResult.CONTINUE;
        }
      });

      return pluginManifest[0];
    }

    try (final JarFile jarFile = new JarFile(file)) {
      final Enumeration<JarEntry> entries = jarFile.entries();
      while (entries.hasMoreElements()) {
        final String entry = entries.nextElement().getName();
        final PluginManifest pluginManifest = getPluginManifestFromEntry(file, entry);
        if (pluginManifest != null)
          return pluginManifest;
      }

      return null;
    }
    catch (final IOException e) {
      throw new IllegalStateException(e);
    }
  }

  public final Type type;
  public final String name;

  private PluginManifest(final Type type, final String name) {
    this.type = type;
    this.name = name;
  }
}
