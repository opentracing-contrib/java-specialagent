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
import java.util.Arrays;

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

  /**
   * Creates a new {@code LibraryFingerprint} with the specified {@code URL}
   * objects referencing JAR files.
   *
   * @param parent The parent {@code ClassLoader} to use for resolution of
   *          classes that should not be part of the fingerprint.
   * @param scanUrls The {@code URL} objects referencing JAR files.
   * @throws NullPointerException If {@code manifest} or {@code urls} is null.
   * @throws IllegalArgumentException If the number of members in {@code urls}
   *           is zero.
   * @throws IOException If an I/O error has occurred.
   */
  LibraryFingerprint(final ClassLoader parent, final URL ... scanUrls) throws IOException {
    if (scanUrls.length == 0)
      throw new IllegalArgumentException("Number of scan URLs must be greater than 0");

    try (final URLClassLoader classLoader = new URLClassLoader(scanUrls, parent)) {
      this.classes = FingerprintBuilder.build(classLoader, Integer.MAX_VALUE, Phase.LOAD);
    }
  }

  /**
   * Creates a new {@code LibraryFingerprint} that is empty.
   */
  LibraryFingerprint() {
    this.classes = null;
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
   * Returns the {@code ClassFingerprint} array of this
   * {@code LibraryFingerprint}.
   *
   * @return The {@code ClassFingerprint} array of this
   *         {@code LibraryFingerprint}.
   */
  ClassFingerprint[] getClasses() {
    return this.classes;
  }

  /**
   * Tests whether the runtime represented by the specified {@code ClassLoader}
   * is compatible with this fingerprint.
   *
   * @param classLoader The {@code ClassLoader} representing the runtime to test
   *          for compatibility.
   * @return An array of {@code FingerprintError} objects representing all
   *         errors encountered in the compatibility test, or {@code null} if
   *         the runtime is compatible with this fingerprint,
   */
  public FingerprintError[] isCompatible(final ClassLoader classLoader) {
    return classes == null ? null : isCompatible(classLoader, new FingerprintVerifier(), 0, 0);
  }

  /**
   * Tests whether the runtime represented by the specified {@code ClassLoader}
   * is compatible with this fingerprint.
   *
   * @param classLoader The {@code ClassLoader} representing the runtime to test
   *          for compatibility.
   * @param fingerprinter The {@code Fingerprinter} to be used for
   *          fingerprinting.
   * @param index The index of the iteration (should be 0 when called).
   * @param depth The depth of the iteration (should be 0 when called).
   * @return An array of {@code FingerprintError} objects representing all
   *         errors encountered in the compatibility test, or {@code null} if
   *         the runtime is compatible with this fingerprint,
   */
  private FingerprintError[] isCompatible(final ClassLoader classLoader, final FingerprintVerifier fingerprinter, final int index, final int depth) {
    for (int i = index; i < classes.length; ++i) {
      FingerprintError error = null;
      try {
        final ClassFingerprint fingerprint = fingerprinter.fingerprint(classLoader, classes[i].getName().replace('.', '/').concat(".class"));
        if (fingerprint == null) {
          fingerprinter.fingerprint(classLoader, classes[i].getName().replace('.', '/').concat(".class"));
          error = new FingerprintError(FingerprintError.Reason.MISSING, classes[i], null);
        }
        else if (!fingerprint.compatible(classes[i])) {
          fingerprint.compatible(classes[i]);
          error = new FingerprintError(FingerprintError.Reason.MISMATCH, classes[i], fingerprint);
        }
      }
      catch (final IOException e) {
        logger.log(Level.WARNING, "Failed generate class fingerprint due to IOException -- resorting to default behavior (permit instrumentation)", e);
      }

      if (error != null) {
        final FingerprintError[] errors = isCompatible(classLoader, fingerprinter, i + 1, depth + 1);
        errors[depth] = error;
        return errors;
      }
    }

    return depth == 0 ? null : new FingerprintError[depth];
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