package io.opentracing.contrib.specialagent;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

class LibraryDigest extends Digest {
  private static final long serialVersionUID = -8454972655262482231L;

  public static LibraryDigest fromFile(final File file) throws IOException {
    try (
      final FileInputStream fis = new FileInputStream(file);
      final ObjectInputStream in = new ObjectInputStream(fis);
    ) {
      return (LibraryDigest)in.readObject();
    }
    catch (final ClassNotFoundException e) {
      throw new UnsupportedOperationException(e);
    }
  }

  private static ClassDigest[] recurse(final URLClassLoader classLoader, final URL[] jarURLs, final int jarIndex, final ZipInputStream in, final int depth) throws IOException {
    Class<?> cls = null;
    do {
      String name;
      do {
        final ZipEntry entry = in.getNextEntry();
        if (entry == null) {
          in.close();
          return jarIndex + 1 < jarURLs.length ? recurse(classLoader, jarURLs, jarIndex + 1, new ZipInputStream(jarURLs[jarIndex].openStream()), depth) : depth == 0 ? null : new ClassDigest[depth];
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

    final ClassDigest digest = new ClassDigest(cls);
    final ClassDigest[] digests = recurse(classLoader, jarURLs, jarIndex, in, depth + 1);
    digests[depth] = digest;
    return digests;
  }

  private final ClassDigest[] classes;

  LibraryDigest(final URL ... urls) throws IOException {
    if (urls.length == 0)
      throw new IllegalArgumentException("Number of arguments must be greater than 0");

    try (final URLClassLoader classLoader = new URLClassLoader(urls)) {
      this.classes = DigestUtil.sort(recurse(classLoader, urls, 0, new ZipInputStream(urls[0].openStream()), 0));
    }
  }

  public void toFile(final File file) throws IOException {
    try (final ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(file))) {
      out.writeObject(this);
    }
  }

  public ClassDigest[] getClasses() {
    return this.classes;
  }

  public ClassDigest[] retainClasses(final LibraryDigest digest) {
    return this.classes == null || digest.classes == null ? null : DigestUtil.retain(classes, digest.classes, 0, 0, 0);
  }

  @Override
  public boolean equals(final Object obj) {
    if (obj == this)
      return true;

    if (!(obj instanceof LibraryDigest))
      return false;

    final LibraryDigest that = (LibraryDigest)obj;
    return classes != null ? that.classes != null && Arrays.equals(classes, that.classes) : that.classes == null;
  }

  @Override
  public String toString() {
    return "\n" + DigestUtil.toString(classes, "\n") ;
  }
}