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
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.objectweb.asm.Opcodes;

final class FingerprintUtil {
  private static final String[] excludePrefixes = {"io.opentracing.", "java.", "javax.crypto.", "javax.net.", "javax.security.", "javax.crypto.", "net.bytebuddy.", "org.ietf.jgss", "org.jcp.xml.dsig.internal.", "org.w3c.dom.", "org.xml.sax.", "sun."};

  static boolean isExcluded(final String className) {
    for (int i = 0; i < excludePrefixes.length; ++i)
      if (className.startsWith(excludePrefixes[i]))
        return true;

    return false;
  }

  /**
   * Tests whether the {@code ACC_SYNTHETIC} bit is set in the specified access
   * modifier.
   *
   * @param mod The access modifier to test.
   * @return {@code true} if the {@code ACC_SYNTHETIC} bit is set in the
   *         specified access modifier.
   */
  static boolean isSynthetic(final int mod) {
    return (mod & Opcodes.ACC_SYNTHETIC) != 0;
  }

  static boolean isPrivate(final int mod) {
    return (mod & Opcodes.ACC_PRIVATE) != 0;
  }

  static boolean isGetStatic(final int opcode) {
    return (opcode & Opcodes.GETSTATIC) != 0;
  }

  static boolean isPutStatic(final int opcode) {
    return (opcode & Opcodes.PUTSTATIC) != 0;
  }

  static boolean isInvokeStatic(final int opcode) {
    return (opcode & Opcodes.INVOKESTATIC) != 0;
  }

  public static boolean isInvokeSpecial(int opcode) {
    return (opcode & Opcodes.INVOKESPECIAL) != 0;
  }

  static void forEachClass(final URLClassLoader classLoader, final BiConsumer<URLClassLoader,String> consumer) throws IOException {
    for (final URL url : classLoader.getURLs()) {
      if (url.getPath().endsWith(".jar")) {
        try (final ZipInputStream in = new ZipInputStream(url.openStream())) {
          for (ZipEntry entry; (entry = in.getNextEntry()) != null;) {
            final String name = entry.getName();
            if (name.endsWith(".class") && !name.startsWith("META-INF/") && !name.startsWith("module-info")) {
              consumer.accept(classLoader, name);
            }
          }
        }
      }
      else {
        final File file = new File(url.getPath());
        final Path path = file.toPath();
        AssembleUtil.recurseDir(file, new Predicate<File>() {
          @Override
          public boolean test(final File t) {
            if (t.isDirectory())
              return true;

            final String name = path.relativize(t.toPath()).toString();
            if (name.endsWith(".class") && !name.startsWith("META-INF/") && !name.startsWith("module-info")) {
              consumer.accept(classLoader, name);
            }

            return true;
          }
        });
      }
    }
  }

  private FingerprintUtil() {
  }
}