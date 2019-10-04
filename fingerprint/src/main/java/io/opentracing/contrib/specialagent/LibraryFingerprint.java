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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A {@link Fingerprint} that represents the fingerprint of a library.
 *
 * @author Seva Safris
 */
public class LibraryFingerprint extends Fingerprint {
  private static final long serialVersionUID = -8454972655262482231L;
  private static final Logger logger = Logger.getLogger(LibraryFingerprint.class);

  /**
   * Returns a {@code LibraryFingerprint} for the serialized object encoding at
   * the specified URL.
   *
   * @param url The URL referencing the resource with the serialized object
   *          encoding representing a {@code LibraryFingerprint} object.
   * @return A {@code LibraryFingerprint} for the serialized object encoding at
   *         the specified URL.
   * @throws IOException If an I/O error has occurred.
   */
  public static LibraryFingerprint fromFile(final URL url) throws IOException {
    try (final ObjectInputStream in = new ObjectInputStream(url.openStream())) {
      final LibraryFingerprint libraryFingerprint = (LibraryFingerprint)in.readObject();
      if (logger.isLoggable(Level.FINEST))
        logger.finest("LibraryFingerprint#fromFile(\"" + url + "\"): " + libraryFingerprint);

      return libraryFingerprint == null ? null : libraryFingerprint;
    }
    catch (final ClassNotFoundException e) {
      throw new UnsupportedOperationException(e);
    }
  }

  private final ClassFingerprint[] classes;
  private final List<String> presents;
  private final List<String> absents;

  /**
   * Creates a new {@code LibraryFingerprint} with the specified {@code URL}
   * objects referencing JAR files.
   *
   * @param classLoader The {@code ClassLoader} to use for resolution of classes
   *          that should not be part of the fingerprint.
   * @param presents List of classes the fingerprint must assert are present.
   * @param absents List of classes the fingerprint must assert are absent.
   * @param logger The logger to be used during the fingerprinting execution.
   * @throws NullPointerException If {@code manifest} or {@code scanUrls} is
   *           null.
   * @throws IllegalArgumentException If the number of members in
   *           {@code scanUrls} is zero.
   * @throws IOException If an I/O error has occurred.
   */
  LibraryFingerprint(final URLClassLoader classLoader, final List<String> presents, final List<String> absents, final Logger logger) throws IOException {
    if (classLoader.getURLs().length == 0)
      throw new IllegalArgumentException("Number of scan URLs must be greater than 0");

    this.classes = new FingerprintBuilder(logger).build(classLoader, Integer.MAX_VALUE).toArray(new ClassFingerprint[0]);
    this.presents = presents;
    this.absents = absents;
  }

  /**
   * Creates a new {@code LibraryFingerprint} that is empty.
   */
  LibraryFingerprint() {
    this.classes = null;
    this.presents = null;
    this.absents = null;
  }

  /**
   * Exports this {@code LibraryFingerprint} to the specified {@code File} in
   * the form of a serialized object representation.
   *
   * @param file The {@code File} to which to export.
   * @throws IOException If an I/O error has occurred.
   */
  void toFile(final File file) throws IOException {
    try (final ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(file))) {
      out.writeObject(this);
    }
  }

  /**
   * @return The {@code ClassFingerprint} array of this
   *         {@code LibraryFingerprint}.
   */
  ClassFingerprint[] getClasses() {
    return this.classes;
  }

  /**
   * @return The list of classes the fingerprint asserts must be present.
   */
  List<String> getPresents() {
    return this.presents;
  }

  /**
   * @return The list of classes the fingerprint asserts must be absent.
   */
  List<String> getAbsents() {
    return this.absents;
  }

  /**
   * Tests whether the runtime represented by the specified {@code ClassLoader}
   * is compatible with this fingerprint.
   *
   * @param classLoader The {@code ClassLoader} representing the runtime to test
   *          for compatibility.
   * @return A list of {@code FingerprintError} objects representing all
   *         errors encountered in the compatibility test, or {@code null} if
   *         the runtime is compatible with this fingerprint.
   */
  public List<FingerprintError> isCompatible(final ClassLoader classLoader) {
    final List<FingerprintError> errors = new ArrayList<>();
    if (presents != null) {
      for (final String present : presents) {
        final String resourcePath = present.replace('.', '/').concat(".class");
        if (classLoader.getResource(resourcePath) == null)
          errors.add(new FingerprintError(FingerprintError.Reason.MUST_BE_PRESENT, new ClassNameFingerprint(present), null));
      }
    }

    if (absents != null) {
      for (final String absent : absents) {
        final String resourcePath = absent.replace('.', '/').concat(".class");
        if (classLoader.getResource(resourcePath) != null)
          errors.add(new FingerprintError(FingerprintError.Reason.MUST_BE_ABSENT, new ClassNameFingerprint(absent), null));
      }
    }

    final FingerprintVerifier verifier = new FingerprintVerifier();
    for (int i = 0; i < classes.length; ++i) {
      try {
        final ClassFingerprint fingerprint = verifier.fingerprint(classLoader, classes[i].getName().replace('.', '/').concat(".class"));
        if (fingerprint == null) {
          verifier.fingerprint(classLoader, classes[i].getName().replace('.', '/').concat(".class"));
          errors.add(new FingerprintError(FingerprintError.Reason.MISSING, classes[i], null));
        }
        else if (!fingerprint.compatible(classes[i])) {
          fingerprint.compatible(classes[i]);
          errors.add(new FingerprintError(FingerprintError.Reason.MISMATCH, classes[i], fingerprint));
        }
        else if (logger.isLoggable(Level.FINER)) {
          logger.finer("ClassFingerprint#compatible[true](\"" + classes[i].getName() + "\")");
        }
      }
      catch (final IOException e) {
        logger.log(Level.WARNING, "Failed generate class fingerprint due to IOException -- resorting to default behavior (permit instrumentation)", e);
      }
    }

    return errors.size() != 0 ? errors : null;
  }

  @Override
  public boolean equals(final Object obj) {
    if (obj == this)
      return true;

    if (!(obj instanceof LibraryFingerprint))
      return false;

    final LibraryFingerprint that = (LibraryFingerprint)obj;
    return classes != null ? that.classes != null && Arrays.equals(classes, that.classes) : that.classes == null;
  }

  @Override
  public String toString() {
    return "\n" + AssembleUtil.toString(classes, "\n");
  }
}