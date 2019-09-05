/* Copyright 2019 The OpenTracing Authors
 *
 * Licensed under the Apache License, final Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, final either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentracing.contrib.specialagent;

import java.io.IOException;
import java.net.URLClassLoader;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

class FingerprintBuilder {
  static boolean debugVisitor = false;
  static boolean debugLog = false;

  private static void debug(final String message, final LogSet logs) {
    if (debugLog)
      System.out.println(message + "\n" + logs.toString());
  }

  static List<ClassFingerprint> build(final ClassLoader classLoader, final int depth, final Class<?> ... classes) throws IOException {
    final LogSet logs = new LogSet(debugVisitor);
    final Fingerprinter fingerprinter = new Fingerprinter(classLoader, logs, debugVisitor);
    for (final Class<?> cls : classes)
      fingerprinter.fingerprint(cls.getName().replace('.', '/').concat(".class"));

    debug("Before compass...", logs);
    fingerprinter.compass(depth);
    debug("After compass...", logs);
    return logs.collate();
  }

  static List<ClassFingerprint> build(final URLClassLoader classLoader, final int depth) throws IOException {
    final LogSet logs = new LogSet(debugVisitor);
    final Fingerprinter fingerprinter = new Fingerprinter(classLoader, logs, debugVisitor);
    final Set<String> excludeClassNames = new HashSet<>();
    AssembleUtil.<Void>forEachClass(classLoader.getURLs(), null, new BiConsumer<String,Void>() {
      @Override
      public void accept(final String name, final Void arg) {
        try {
          fingerprinter.fingerprint(name);
          excludeClassNames.add(name.substring(0, name.length() - 6).replace('/', '.'));
        }
        catch (final IOException e) {
          throw new IllegalStateException(e);
        }
      }
    });

    debug("Before compass...", logs);
    fingerprinter.compass(depth);
    debug("After compass...", logs);
    logs.purge(excludeClassNames);
    debug("After purge...", logs);
    return logs.collate();
  }

  private FingerprintBuilder() {
  }
}