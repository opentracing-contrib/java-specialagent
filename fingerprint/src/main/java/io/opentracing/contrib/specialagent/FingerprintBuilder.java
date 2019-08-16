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

class FingerprintBuilder {
  static boolean debugVisitor = false;
  static Phase debugLog = null;

  private static void debug(final LogSet logs) {
    if (debugLog != null)
      System.out.println(logs.toString(debugLog));
  }

  static ClassFingerprint[] build(final ClassLoader classLoader, final int depth, final Phase phase, final Class<?> ... classes) throws IOException {
    final LogSet logs = new LogSet(debugVisitor);
    final Fingerprinter fingerprinter = new Fingerprinter(classLoader, logs, debugVisitor);
    for (final Class<?> cls : classes)
      fingerprinter.fingerprint(Phase.LOAD, cls.getName().replace('.', '/').concat(".class"));

    fingerprinter.compass(depth);
    debug(logs);
    return logs.collate(phase);
  }

  static ClassFingerprint[] build(final URLClassLoader classLoader, final int depth, final Phase phase) throws IOException {
    final LogSet logs = new LogSet(debugVisitor);
    final Fingerprinter fingerprinter = new Fingerprinter(classLoader, logs, debugVisitor);
    AssembleUtil.<Void>forEachClass(classLoader.getURLs(), null, new BiConsumer<String,Void>() {
      @Override
      public void accept(final String name, final Void arg) {
        try {
          fingerprinter.fingerprint(Phase.LOAD, name);
        }
        catch (final IOException e) {
          throw new IllegalStateException(e);
        }
      }
    });

    fingerprinter.compass(depth);
    debug(logs);
    return logs.collate(phase);
  }

  private FingerprintBuilder() {
  }
}