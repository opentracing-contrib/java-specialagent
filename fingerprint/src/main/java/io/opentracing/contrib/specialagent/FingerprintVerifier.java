/* Copyright 2018 The OpenTracing Authors
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.objectweb.asm.ClassVisitor;

/**
 * An ASM {@link ClassVisitor} that verifies {@link Fingerprint} objects for
 * classes in a {@code ClassLoader}.
 *
 * @author Seva Safris
 */
class FingerprintVerifier {
  private static final Logger logger = Logger.getLogger(FingerprintVerifier.class);

  /**
   * Creates a new {@code Fingerprinter}.
   */
  FingerprintVerifier() {
    super();
  }

  private final Map<String,ClassFingerprint> classNameToFingerprint = new HashMap<>();
  private final Set<String> innerClassExcludes = new HashSet<>();

  /**
   * Fingerprints all class resources in the specified {@code ClassLoader}.
   * <p>
   * <i><b>Note:</b> Classes under {@code /META-INF} or {@code /module-info} are
   * not fingerprinted</i>.
   *
   * @param classLoader The {@code ClassLoader} in which the resource path is to
   *          be found.
   * @return An array of {@code ClassFingerprint} objects representing the
   *         fingerprints of all class resources in the specified
   *         {@code ClassLoader}.
   * @throws IOException If an I/O error has occurred.
   */
  ClassFingerprint[] fingerprint(final URLClassLoader classLoader) throws IOException {
    AssembleUtil.<Void>forEachClass(classLoader.getURLs(), null, new BiConsumer<String,Void>() {
      @Override
      public void accept(final String name, final Void arg) {
        try {
          final ClassFingerprint classFingerprint = fingerprint(classLoader, name);
          if (classFingerprint != null)
            classNameToFingerprint.put(classFingerprint.getName(), classFingerprint);
        }
        catch (final IOException e) {
          throw new IllegalStateException(e);
        }
      }
    });
    classNameToFingerprint.keySet().removeAll(innerClassExcludes);
    return AssembleUtil.sort(classNameToFingerprint.values().toArray(new ClassFingerprint[classNameToFingerprint.size()]));
  }

  /**
   * Fingerprints the provided resource path representing a class in the
   * specified {@code ClassLoader}.
   *
   * @param classLoader The {@code ClassLoader} in which the resource path is to
   *          be found.
   * @param resourcePath The resource path to fingerprint.
   * @return A {@code ClassFingerprint} object representing the fingerprint of
   *         the class at the specified resource path.
   * @throws IOException If an I/O error has occurred.
   */
  ClassFingerprint fingerprint(final ClassLoader classLoader, final String resourcePath) throws IOException {
    if (logger.isLoggable(Level.FINEST))
      logger.finest(AssembleUtil.getNameId(this) + "#fingerprint(" + AssembleUtil.getNameId(classLoader) + ", \"" + resourcePath + "\")");

    return ClassScanner.fingerprint(classLoader, resourcePath, innerClassExcludes);
  }
}