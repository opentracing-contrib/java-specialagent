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
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

class LibraryFingerprint extends Fingerprint {
  private static final long serialVersionUID = -8454972655262482231L;
  private static final Logger logger = Logger.getLogger(LibraryFingerprint.class.getName());

  static LibraryFingerprint fromFile(final URL url) throws IOException {
    try (final ObjectInputStream in = new ObjectInputStream(url.openStream())) {
      return (LibraryFingerprint)in.readObject();
    }
    catch (final ClassNotFoundException e) {
      throw new UnsupportedOperationException(e);
    }
  }

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
      while (!name.endsWith(".class"));

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

  LibraryFingerprint(final URL ... urls) throws IOException {
    if (urls.length == 0)
      throw new IllegalArgumentException("Number of arguments must be greater than 0");

    try (final URLClassLoader classLoader = new URLClassLoader(urls)) {
      this.classes = Util.sort(recurse(classLoader, urls, 0, new ZipInputStream(urls[0].openStream()), 0));
    }
  }

  public void toFile(final File file) throws IOException {
    try (final ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(file))) {
      out.writeObject(this);
    }
  }

  public ClassFingerprint[] getClasses() {
    return this.classes;
  }

  public ClassFingerprint[] retainClasses(final LibraryFingerprint fingerprint) {
    return this.classes == null || fingerprint.classes == null ? null : Util.retain(classes, fingerprint.classes, 0, 0, 0);
  }

  private class TempClassLoader extends ClassLoader {
    private final ClassLoader classLoader;

    private TempClassLoader(final ClassLoader classLoader) {
      super(null);
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
        logger.log(Level.SEVERE, "Failed to read bytes for " + resourceName, e);
        return null;
      }
    }
  }

  public FingerprintError[] matchesRuntime(final ClassLoader classLoader, final int start, final int depth) {
    final TempClassLoader tempClassLoader = new TempClassLoader(classLoader);
    for (int i = start; i < classes.length; ++i) {
      FingerprintError error = null;
      try {
        final Class<?> cls = Class.forName(classes[i].getName(), false, tempClassLoader);
        final ClassFingerprint fingerprint = new ClassFingerprint(cls);
        if (!fingerprint.compatible(classes[i]))
          error = new FingerprintError(FingerprintError.Reason.MISMATCH, classes[i], fingerprint);
      }
      catch (final ClassNotFoundException e) {
        error = new FingerprintError(FingerprintError.Reason.MISSING, classes[i], null);
      }

      if (error != null) {
        final FingerprintError[] errors = matchesRuntime(classLoader, i + 1, depth + 1);
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
    return "\n" + Util.toString(classes, "\n") ;
  }
}