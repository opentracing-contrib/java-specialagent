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
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * A {@link Fingerprint} that represents the fingerprint of a library.
 */
class LibraryFingerprint extends Fingerprint {
  private static final long serialVersionUID = -8454972655262482231L;
  private static final Logger logger = Logger.getLogger(LibraryFingerprint.class.getName());

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
  static LibraryFingerprint fromFile(final URL url) throws IOException {
    try (final ObjectInputStream in = new ObjectInputStream(url.openStream())) {
      return (LibraryFingerprint)in.readObject();
    }
    catch (final ClassNotFoundException e) {
      throw new UnsupportedOperationException(e);
    }
  }

  /**
   * Returns an array of {@code ClassFingerprint} objects for the non-private,
   * non-interface, and non-synthetic classes present in the specified
   * {@code jarURLs}, belonging to the provided {@code ClassLoader}. This is a
   * recursive algorithm, and the {@code jarIndex}, {@code in}, and
   * {@code depth} parameters are used to track the execution state on the call
   * stack.
   *
   * @param classLoader The {@code ClassLoader} in which the specified
   *          {@code jarURLs} are classpath entries.
   * @param jarURLs The {@code URL} objects identifying JAR files to be scanned
   *          for classes.
   * @param jarIndex The iteration index of the {@code jarURLs} parameter
   *          (should be 0 when called).
   * @param in The {@code ZipInputStream} for the JAR at the current iteration's
   *          {@code jarURLs}.
   * @param depth The depth of the iteration (should be 0 when called).
   * @return An array of {@code ConstructorFingerprint} objects for the
   *         non-private and non-synthetic constructors in the specified array
   *         of {@code Constructor} objects.
   */
  private static ClassFingerprint[] recurse(final URLClassLoader classLoader, final URL[] jarURLs, final int jarIndex, final ZipInputStream in, final int depth) throws IOException {
    Class<?> cls = null;
    do {
      String name;
      do {
        final ZipEntry entry = in.getNextEntry();
        if (entry == null) {
          in.close();
          return jarIndex + 1 < jarURLs.length ? recurse(classLoader, jarURLs, jarIndex + 1, new ZipInputStream(jarURLs[jarIndex + 1].openStream()), depth) : depth == 0 ? null : new ClassFingerprint[depth];
        }

        name = entry.getName();
      }
      while (!name.endsWith(".class") || name.startsWith("META-INF/") || name.startsWith("module-info"));

      try {
        cls = Class.forName(name.substring(0, name.length() - 6).replace('/', '.'), false, classLoader);
      }
      catch (final ClassNotFoundException e) {
      }
    }
    while (cls == null || cls.isInterface() || cls.isSynthetic() || Modifier.isPrivate(cls.getModifiers()));

    final ClassFingerprint fingerprint = new ClassFingerprint(cls);
    final ClassFingerprint[] fingerprints = recurse(classLoader, jarURLs, jarIndex, in, depth + 1);
    fingerprints[depth] = fingerprint;
    return fingerprints;
  }

  private final ClassFingerprint[] classes;

  /**
   * Creates a new {@code LibraryFingerprint} with the specified {@code URL}
   * objects referencing JAR files.
   *
   * @param parent The parent {@code ClassLoader} to use for resolution of
   *          classes that should not be part of the fingerprint.
   * @param urls The {@code URL} objects referencing JAR files.
   * @throws IOException If an I/O error has occurred.
   */
  LibraryFingerprint(final ClassLoader parent, final URL ... urls) throws IOException {
    if (urls.length == 0)
      throw new IllegalArgumentException("Number of arguments must be greater than 0");

    try (final URLClassLoader classLoader = new URLClassLoader(urls, parent)) {
      this.classes = Util.sort(recurse(classLoader, urls, 0, new ZipInputStream(urls[0].openStream()), 0));
    }
  }

  /**
   * Exports this {@code LibraryFingerprint} to the specified {@code File} in
   * the form of a serialized object representation.
   *
   * @param file The {@code File} to which to export.
   * @throws IOException If an I/O error has occurred.
   */
  public void toFile(final File file) throws IOException {
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
  public ClassFingerprint[] getClasses() {
    return this.classes;
  }

  /**
   * A dispensable {@code ClassLoader} used for dereferencing class names when
   * evaluating compatibility of a fingerprint to that of a runtime.
   */
  private class TempClassLoader extends ClassLoader {
    private final ClassLoader classLoader;

    /**
     * Creates a new {@code TempClassLoader} with the specified
     * {@code ClassLoader} as its source.
     *
     * @param classLoader The {@code ClassLoader} used as the source for loading
     *          of bytecode.
     */
    private TempClassLoader(final ClassLoader classLoader, final ClassLoader parent) {
      super(parent);
      this.classLoader = classLoader;
    }

    @Override
    protected Class<?> findClass(final String name) throws ClassNotFoundException {
      final String resourceName = name.replace('.', '/').concat(".class");
      try (final InputStream in = classLoader.getResourceAsStream(resourceName)) {
        if (in == null)
          return null;

        final byte[] bytes = Util.readBytes(in);
        return defineClass(name, bytes, 0, bytes.length, null);
      }
      catch (final IOException e) {
        logger.log(Level.SEVERE, "Failed to read bytes for: " + resourceName, e);
        return null;
      }
    }
  }

  /**
   * Tests whether the runtime represented by the specified {@code ClassLoader}
   * is compatible with this fingerprint.
   *
   * @param classLoader The {@code ClassLoader} representing the runtime to test
   *          for compatibility.
   * @param index The index of the iteration (should be 0 when called).
   * @param depth The depth of the iteration (should be 0 when called).
   * @return An array of @{@code FingerprintError} objects representing all
   *         errors encountered in the compatibility test, or {@code null} if
   *         the runtime is compatible with this fingerprint,
   */
  public FingerprintError[] isCompatible(final ClassLoader classLoader, final ClassLoader parentClassLoader, final int index, final int depth) {
    final TempClassLoader tempClassLoader = new TempClassLoader(classLoader, parentClassLoader);
    for (int i = index; i < classes.length; ++i) {
      FingerprintError error = null;
      try {
        final Class<?> cls = Class.forName(classes[i].getName(), false, tempClassLoader);
        try {
          final ClassFingerprint fingerprint = new ClassFingerprint(cls);
          if (!fingerprint.compatible(classes[i]))
            error = new FingerprintError(FingerprintError.Reason.MISMATCH, classes[i], fingerprint);
        }
        catch (final VerifyError e) {
          logger.log(Level.WARNING, "Failed generate class fingerprint due to VerifyError -- resorting to default behavior (permit instrumentation)", e);
        }
      }
      catch (final ClassNotFoundException e) {
        error = new FingerprintError(FingerprintError.Reason.MISSING, classes[i], null);
      }

      if (error != null) {
        final FingerprintError[] errors = isCompatible(classLoader, parentClassLoader, i + 1, depth + 1);
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
    return "\n" + Util.toString(classes, "\n");
  }
}