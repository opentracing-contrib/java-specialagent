/*
 * Copyright 2017 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.opentracing.contrib.specialagent;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.net.JarURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

final class Util {
  private static final int DEFAULT_SOCKET_BUFFER_SIZE = 65536;

  /**
   * Returns the number of occurrences of the specified {@code char} in the
   * specified {@code String}.
   *
   * @param s The string.
   * @param c The char.
   * @return The number of occurrences of the specified {@code char} in the
   *         specified {@code String}.
   * @throws NullPointerException If {@code s} is null.
   */
  static int getOccurrences(final String s, final char c) {
    int count = 0;
    for (int i = 0; i < s.length(); ++i)
      if (s.charAt(i) == c)
        ++count;

    return count;
  }

  /**
   * Returns a string representation of the specified array, using the specified
   * delimiter between the string representation of each element. If the
   * specified array is null, this method returns the string {@code "null"}. If
   * the length of the specified array is 0, this method returns {@code ""}.
   *
   * @param a The array.
   * @param del The delimiter.
   * @return A string representation of the specified array, using the specified
   *         delimiter between the string representation of each element.
   */
  static String toString(final Object[] a, final String del) {
    if (a == null)
      return "null";

    if (a.length == 0)
      return "";

    final StringBuilder builder = new StringBuilder();
    for (int i = 0; i < a.length; ++i) {
      if (i > 0)
        builder.append(del);

      builder.append(String.valueOf(a[i]));
    }

    return builder.toString();
  }

  /**
   * Returns string representation of the specified array.
   * <p>
   * This method differentiates itself from the algorithm in
   * {@link Arrays#toString(Object[])} by formatting the output to separate
   * entries onto new lines, indented with 2 spaces. If the specified array is
   * null, this method returns the string {@code "null"}. If the length of the
   * specified array is 0, this method returns {@code ""}.
   *
   * @param a The array.
   * @return An indented string representation of the specified array, using the
   *         algorithm in {@link Arrays#toString(Object[])}.
   */
  static String toIndentedString(final Object[] a) {
    if (a == null)
      return "null";

    if (a.length == 0)
      return "";

    final StringBuilder builder = new StringBuilder();
    for (int i = 0; i < a.length; ++i) {
      if (i > 0)
        builder.append('\n');

      builder.append("  ").append(a[i]);
    }

    return builder.toString();
  }

  /**
   * Returns string representation of the specified array.
   * <p>
   * This method differentiates itself from the algorithm in
   * {@link Collection#toString()} by formatting the output to separate entries
   * onto new lines, indented with 2 spaces. If the specified array is null,
   * this method returns the string {@code "null"}. If the length of the
   * specified array is 0, this method returns {@code ""}.
   *
   * @param l The array.
   * @return An indented string representation of the specified {@code List},
   *         using the algorithm in {@link Collection#toString()}.
   */
  static String toIndentedString(final Collection<?> l) {
    if (l == null)
      return "null";

    if (l.size() == 0)
      return "";

    final StringBuilder builder = new StringBuilder();
    final Iterator<?> iterator = l.iterator();
    for (int i = 0; iterator.hasNext(); ++i) {
      if (i > 0)
        builder.append('\n');

      builder.append("  ").append(iterator.next());
    }

    return builder.toString();
  }

  /**
   * Returns an array of {@code URL} objects representing each path entry in the
   * specified {@code classpath}.
   *
   * @param classpath The classpath which to convert to an array of {@code URL}
   *          objects.
   * @return An array of {@code URL} objects representing each path entry in the
   *         specified {@code classpath}.
   */
  static URL[] classPathToURLs(final String classpath) {
    final String[] paths = classpath.split(File.pathSeparator);
    final URL[] libs = new URL[paths.length];
    try {
      for (int i = 0; i < paths.length; ++i)
        libs[i] = new File(paths[i]).toURI().toURL();
    }
    catch (final MalformedURLException e) {
      throw new UnsupportedOperationException(e);
    }

    return libs;
  }

  /**
   * Returns the array of bytes read from an {@code InputStream}.
   *
   * @param in The {@code InputStream}.
   * @return The array of bytes read from an {@code InputStream}.
   * @throws IOException If an I/O error has occurred.
   */
  static byte[] readBytes(final InputStream in) throws IOException {
    final ByteArrayOutputStream buffer = new ByteArrayOutputStream(DEFAULT_SOCKET_BUFFER_SIZE);
    final byte[] data = new byte[DEFAULT_SOCKET_BUFFER_SIZE];
    for (int len; (len = in.read(data)) != -1; buffer.write(data, 0, len));
    return buffer.toByteArray();
  }

  /**
   * Returns the hexadecimal representation of an object's identity hash code.
   *
   * @param obj The object.
   * @return The hexadecimal representation of an object's identity hash code.
   */
  static String getIdentityCode(final Object obj) {
    return obj.getClass().getName() + "@" + Integer.toString(System.identityHashCode(obj), 16);
  }

  /**
   * Returns a {@code List} of {@code URL} objects having a prefix path that
   * matches {@code path}. This method will add a shutdown hook to delete any
   * temporary directory and file resources it created.
   *
   * @param path The prefix path to match when finding resources.
   * @return A {@code List} of {@code URL} objects having a prefix path that
   *         matches {@code path}.
   * @throws IllegalStateException If an illegal state occurs due to an
   *           {@link IOException}.
   */
  static List<URL> findResources(final String path) {
    try {
      final Enumeration<URL> resources = ClassLoader.getSystemClassLoader().getResources(path);
      if (!resources.hasMoreElements())
        return null;

      final List<URL> urls = new ArrayList<>();
      File destDir = null;
      do {
        final URL resource = resources.nextElement();
        final URLConnection connection = resource.openConnection();
        // Only consider resources that are inside JARs
        if (!(connection instanceof JarURLConnection))
          continue;

        if (destDir == null)
          destDir = Files.createTempDirectory("opentracing-specialagent").toFile();

        final JarURLConnection jarURLConnection = (JarURLConnection)connection;
        jarURLConnection.setUseCaches(false);
        final JarFile jarFile = jarURLConnection.getJarFile();
        final Enumeration<JarEntry> entries = jarFile.entries();
        while (entries.hasMoreElements()) {
          final String entry = entries.nextElement().getName();
          if (entry.length() > path.length() && entry.startsWith(path)) {
            final int slash = entry.lastIndexOf('/');
            final File subDir = new File(destDir, entry.substring(0, slash));
            subDir.mkdirs();
            final File file = new File(subDir, entry.substring(slash + 1));
            final URL url = new URL(resource, entry.substring(path.length()));
            Files.copy(url.openStream(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            urls.add(file.toURI().toURL());
          }
        }
      }
      while (resources.hasMoreElements());

      if (destDir != null) {
        final File targetDir = destDir;
        Runtime.getRuntime().addShutdownHook(new Thread() {
          @Override
          public void run() {
            deleteDir(targetDir);
          }
        });
      }

      return urls;
    }
    catch (final IOException e) {
      throw new IllegalStateException(e);
    }
  }

  /**
   * Recursively delete a directory and its contents.
   *
   * @param dir The directory to delete.
   * @return {@code true} if {@code dir} was successfully deleted, {@code false}
   *         otherwise.
   */
  private static boolean deleteDir(final File dir) {
    final File[] files = dir.listFiles();
    if (files != null)
      for (final File file : files)
        deleteDir(file);

    return dir.delete();
  }

  /**
   * Returns the name of the specified {@code Class} as per the following rules:
   * <ul>
   * <li>If {@code cls} represents {@code void}, this method returns
   * {@code null}</li>
   * <li>If {@code cls} represents an array, this method returns the code
   * semantics representation (i.e. {@code java.lang.Object[]})</li>
   * <li>Otherwies, this method return {@code cls.getName()}</li>
   * </ul>
   *
   * @param cls The class.
   * @return The name of the specified {@code Class}
   */
  static String getName(final Class<?> cls) {
    return cls == Void.TYPE ? null : cls.isArray() ? cls.getComponentType().getName() + "[]" : cls.getName();
  }

  /**
   * Returns an array of {@code String} class names by calling
   * {@link #getName(Class)}) on each element in the specified array of
   * {@code Class} objects; If the length of the specified array is 0, this
   * method returns {@code null}.
   *
   * @param classes The array of {@code Class} objects..
   * @return An array of {@code String} class names by calling
   *         {@link #getName(Class)}) on each element in the specified array of
   *         {@code Class} objects; If the length of the specified array is 0,
   *         this method returns {@code null}.
   * @throws NullPointerException If {@code classes} is null.
   */
  static String[] getNames(final Class<?>[] classes) {
    if (classes.length == 0)
      return null;

    final String[] names = new String[classes.length];
    for (int i = 0; i < classes.length; ++i)
      names[i] = getName(classes[i]);

    return names;
  }

  /**
   * Sorts the specified array of objects into ascending order, according to the
   * natural ordering of its elements. All elements in the array must implement
   * the {@link Comparable} interface. Furthermore, all elements in the array
   * must be mutually comparable (that is, {@code e1.compareTo(e2)} must not
   * throw a {@link ClassCastException} for any elements {@code e1} and
   * {@code e2} in the array).
   *
   * @param array The array to be sorted.
   * @return The specified array, which is sorted in-place (unless it is null).
   * @see Arrays#sort(Object[])
   */
  static <T>T[] sort(final T[] array) {
    if (array == null)
      return null;

    Arrays.sort(array);
    return array;
  }

  /**
   * Returns an array of type {@code <T>} that includes only the elements that
   * belong to the first and second specified array (the specified arrays must
   * be sorted).
   * <p>
   * <i><b>Note:</b> This is a recursive algorithm, implemented to take
   * advantage of the high performance of callstack registers, but will fail due
   * to a {@link StackOverflowError} if the number of differences between the
   * first and second specified arrays approaches ~80,000.</i>
   *
   * @param <T> Type parameter of array.
   * @param a The first specified array (sorted).
   * @param b The second specified array (sorted).
   * @param i The starting index of the first specified array (should be set to
   *          0).
   * @param j The starting index of the second specified array (should be set to
   *          0).
   * @param r The starting index of the resulting array (should be set to 0).
   * @return An array of type {@code <T>} that includes only the elements that
   *         belong to the first and second specified array (the specified
   *         arrays must be sorted).
   * @throws NullPointerException If {@code a} or {@code b} are null.
   */
  @SuppressWarnings("unchecked")
  static <T extends Comparable<T>>T[] retain(final T[] a, final T[] b, final int i, final int j, final int r) {
    for (int d = 0;; ++d) {
      int comparison = 0;
      if (i + d == a.length || j + d == b.length || (comparison = a[i + d].compareTo(b[j + d])) != 0) {
        final T[] retained;
        if (i + d == a.length || j + d == b.length)
          retained = r + d == 0 ? null : (T[])Array.newInstance(a.getClass().getComponentType(), r + d);
        else if (comparison < 0)
          retained = retain(a, b, i + d + 1, j + d, r + d);
        else
          retained = retain(a, b, i + d, j + d + 1, r + d);

        if (d > 0)
          System.arraycopy(a, i, retained, r, d);

        return retained;
      }
    }
  }

  private Util() {
  }
}