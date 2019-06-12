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
import java.util.List;

class FingerprintBuilder {
  static boolean debugVisitor = false;
  static Phase debugLog = null;

  private static void debug(final LogSet logs) {
    if (debugLog != null)
      System.out.println(logs.toString(debugLog));
  }

  static List<ClassFingerprint> build(final ClassLoader classLoader, final Class<?> ... classes) {
    final LogSet logs = new LogSet(debugVisitor);
    final Fingerprinter fingerprinter = new Fingerprinter(classLoader, logs, debugVisitor);
    for (final Class<?> cls : classes)
      fingerprinter.fingerprint(Phase.LOAD, cls.getName().replace('.', '/').concat(".class"));

    fingerprinter.analyze();
    debug(logs);
    return logs.collate();
  }

  static List<ClassFingerprint> build(final URLClassLoader classLoader) {
    final LogSet logs = new LogSet(debugVisitor);
    final Fingerprinter fingerprinter = new Fingerprinter(classLoader, logs, debugVisitor);
    try {
      FingerprintUtil.forEachClass(classLoader, new BiConsumer<URLClassLoader,String>() {
        @Override
        public void accept(final URLClassLoader t, final String u) {
          fingerprinter.fingerprint(Phase.LOAD, u);
        }
      });
    }
    catch (final IOException e) {
      throw new IllegalStateException(e);
    }

    fingerprinter.analyze();
    debug(logs);
    return logs.collate();
  }

  private FingerprintBuilder() {
  }
}