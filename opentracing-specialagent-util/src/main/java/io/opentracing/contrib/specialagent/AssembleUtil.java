/* Copyright 2019 The OpenTracing Authors
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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

public final class AssembleUtil {
  private static final int DEFAULT_SOCKET_BUFFER_SIZE = 65536;
  private static final String[] scopes = {"compile", "provided", "runtime", "system", "test"};

  /**
   * Tests if the specified string is a name of a Maven scope.
   *
   * @param scope The string to test.
   * @return {@code true} if the specified string is a name of a Maven scope.
   */
  private static boolean isScope(final String scope) {
    for (int i = 0; i < scopes.length; ++i)
      if (scopes[i].equals(scope))
        return true;

    return false;
  }

  private static final Set<String> jarTypes = new HashSet<>(Arrays.asList("jar", "test-jar", "maven-plugin", "ejb", "ejb-client", "java-source", "javadoc"));

  /** https://maven.apache.org/ref/3.6.1/maven-core/artifact-handlers.html */
  private static String getExtension(final String type) {
    return type == null || jarTypes.contains(type) ? "jar" : type;
  }

  private static boolean contains(final Object[] array, final Object obj) {
    for (int i = 0; i < array.length; ++i)
      if (obj == null ? array[i] == null : obj.equals(array[i]))
        return true;

    return false;
  }

  public static File getFileForDependency(final String dependency, final String ... scopes) {
    final int c0 = dependency.indexOf(':');
    final String groupId = dependency.substring(0, c0);

    // artifactId
    final int c1 = dependency.indexOf(':', c0 + 1);
    final String artifactId = dependency.substring(c0 + 1, c1);

    // type
    final int c2 = dependency.indexOf(':', c1 + 1);
    final String type = dependency.substring(c1 + 1, c2);
    final String ext = getExtension(type);

    // classifier or version
    final int c3 = dependency.indexOf(':', c2 + 1);
    final String classifierOrVersion = dependency.substring(c2 + 1, c3 > c2 ? c3 : dependency.length());

    // version or scope
    final int c4 = c3 == -1 ? -1 : dependency.indexOf(':', c3 + 1);
    final String versionOrScope = c3 == -1 ? null : dependency.substring(c3 + 1, c4 > c3 ? c4 : dependency.length());

    final String scope = c4 == -1 ? null : dependency.substring(c4 + 1);

    final String groupPath = groupId.replace('.', File.separatorChar) + File.separator + artifactId + File.separator;
    if (scope != null) {
      if (scopes != null && !contains(scopes, scope))
        return null;

      return new File(groupPath + versionOrScope, artifactId + "-" + versionOrScope + "-" + classifierOrVersion + "." + ext);
    }
    else if (versionOrScope != null) {
      final boolean isScope = isScope(versionOrScope);
      if (scopes != null && (isScope ? !contains(scopes, versionOrScope) : !contains(scopes, "compile")))
        return null;

      if (isScope)
        return new File(groupPath + classifierOrVersion, artifactId + "-" + classifierOrVersion + "." + ext);

      return new File(groupPath + classifierOrVersion, artifactId + "-" + versionOrScope + "-" + classifierOrVersion + "." + ext);
    }
    else {
      if (scopes != null && !contains(scopes, "compile"))
        return null;

      return new File(groupPath + classifierOrVersion, artifactId + "-" + classifierOrVersion + "." + ext);
    }
  }

  /**
   * Selects the resource names from the specified TGF-formatted string of Maven
   * dependencies {@code tgf} that match the specification of the following
   * parameters.
   *
   * @param tgf The TGF-formatted string of Maven dependencies.
   * @param includeOptional Whether to include dependencies marked as
   *          {@code (optional)}.
   * @param scopes An array of Maven scopes to include in the returned set, or
   *          {@code null} to include all scopes.
   * @param excludes An array of {@code Class} objects representing source
   *          locations to be excluded from the returned set.
   * @return A {@code Set} of resource names that match the call parameters.
   * @throws IOException If an I/O error has occurred.
   */
  public static Set<File> selectFromTgf(final String tgf, final boolean includeOptional, final String[] scopes, final Class<?> ... excludes) throws IOException {
    final Set<String> excluded = getLocations(excludes);
    final Set<File> files = new HashSet<>();
    final StringTokenizer tokenizer = new StringTokenizer(tgf, "\r\n");
    TOKENIZER:
    while (tokenizer.hasMoreTokens()) {
      String token = tokenizer.nextToken().trim();
      if ("#".equals(token))
        break;

      final boolean isOptional = token.endsWith(" (optional)");
      if (isOptional) {
        if (!includeOptional)
          continue;

        token = token.substring(0, token.length() - 11);
      }

      // groupId
      final int start = token.indexOf(' ');
      int end = token.indexOf(' ', start + 1);
      if (end == -1)
        end = token.length();

      final File file = getFileForDependency(token.substring(start + 1, end), scopes);
      if (file == null)
        continue;

      for (final String exclude : excluded)
        if (exclude.endsWith(file.getName()))
          continue TOKENIZER;

      files.add(file);
    }

    return files;
  }

  public static boolean hasFileInJar(final File jarFile, final String name) throws IOException {
    try (final ZipFile zipFile = new ZipFile(jarFile)) {
      return getEntryFromJar(zipFile, name) != null;
    }
  }

  public static String readFileFromJar(final File jarFile, final String name) throws IOException {
    try (final ZipFile zipFile = new ZipFile(jarFile)) {
      final ZipEntry entry = getEntryFromJar(zipFile, name);
      if (entry == null)
        return null;

      try (final InputStream in = zipFile.getInputStream(entry)) {
        return new String(readBytes(in));
      }
    }
  }

  private static ZipEntry getEntryFromJar(final ZipFile zipFile, final String name) {
    final Enumeration<? extends ZipEntry> enumeration = zipFile.entries();
    while (enumeration.hasMoreElements()) {
      final ZipEntry entry = enumeration.nextElement();
      if (entry.getName().equals(name))
        return entry;
    }

    return null;
  }

  /**
   * Returns a {@code Set} of string paths representing the classpath locations
   * of the specified classes.
   *
   * @param classes The classes for which to return a {@code Set} of classpath
   *          paths.
   * @return A {@code Set} of string paths representing the classpath locations
   *         of the specified classes.
   * @throws IOException If an I/O error has occurred.
   */
  public static Set<String> getLocations(final Class<?> ... classes) throws IOException {
    final ClassLoader classLoader = ClassLoader.getSystemClassLoader();
    final Set<String> locations = new LinkedHashSet<>();
    for (final Class<?> cls : classes) {
      final String resourceName = cls.getName().replace('.', '/').concat(".class");
      final Enumeration<URL> resources = classLoader.getResources(resourceName);
      while (resources.hasMoreElements()) {
        final String resource = resources.nextElement().getFile();
        locations.add(resource.startsWith("file:") ? resource.substring(5, resource.indexOf('!')) : resource.substring(0, resource.length() - resourceName.length() - 1));
      }
    }

    return locations;
  }

  /**
   * Returns the array of bytes read from the specified {@code URL}.
   *
   * @param url The {@code URL} from which to read bytes.
   * @return The array of bytes read from an {@code InputStream}.
   * @throws NullPointerException If the specified {@code URL} is null.
   */
  public static byte[] readBytes(final URL url) {
    try {
      try (final InputStream in = url.openStream()) {
        return readBytes(in);
      }
    }
    catch (final IOException e) {
      throw new IllegalStateException(e);
    }
  }

  /**
   * Returns the array of bytes read from the specified {@code InputStream}.
   *
   * @param in The {@code InputStream} from which to read bytes.
   * @return The array of bytes read from an {@code InputStream}.
   * @throws IOException If an I/O error has occurred.
   * @throws NullPointerException If the specified {@code InputStream} is null.
   */
  public static byte[] readBytes(final InputStream in) throws IOException {
    final ByteArrayOutputStream buffer = new ByteArrayOutputStream(DEFAULT_SOCKET_BUFFER_SIZE);
    final byte[] bytes = new byte[DEFAULT_SOCKET_BUFFER_SIZE];
    for (int len; (len = in.read(bytes)) != -1;)
      if (len != 0)
        buffer.write(bytes, 0, len);

    return buffer.toByteArray();
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
  public static String toIndentedString(final Object[] a) {
    if (a == null)
      return "null";

    if (a.length == 0)
      return "";

    final StringBuilder builder = new StringBuilder();
    for (int i = 0; i < a.length; ++i) {
      if (i > 0)
        builder.append(",\n");

      builder.append(a[i]);
    }

    return builder.toString();
  }

  /**
   * Returns string representation of the specified collection.
   * <p>
   * This method differentiates itself from the algorithm in
   * {@link Collection#toString()} by formatting the output to separate entries
   * onto new lines, indented with 2 spaces. If the specified collection is
   * null, this method returns the string {@code "null"}. If the size of the
   * specified collection is 0, this method returns {@code ""}.
   *
   * @param l The collection.
   * @return An indented string representation of the specified {@code List},
   *         using the algorithm in {@link Collection#toString()}.
   */
  public static String toIndentedString(final Collection<?> l) {
    if (l == null)
      return "null";

    if (l.size() == 0)
      return "";

    final StringBuilder builder = new StringBuilder();
    final Iterator<?> iterator = l.iterator();
    for (int i = 0; iterator.hasNext(); ++i) {
      if (i > 0)
        builder.append(",\n");

      builder.append(iterator.next());
    }

    return builder.toString();
  }

  /**
   * Returns string representation of the specified map.
   * <p>
   * This method differentiates itself from the algorithm in
   * {@link Map#toString()} by formatting the output to separate entries
   * onto new lines, indented with 2 spaces. If the specified map is
   * null, this method returns the string {@code "null"}. If the size of the
   * specified map is 0, this method returns {@code ""}.
   *
   * @param m The map.
   * @return An indented string representation of the specified {@code List},
   *         using the algorithm in {@link Map#toString()}.
   */
  public static String toIndentedString(final Map<?,?> m) {
    if (m == null)
      return "null";

    if (m.size() == 0)
      return "";

    final StringBuilder builder = new StringBuilder();
    final Iterator<?> iterator = m.entrySet().iterator();
    for (int i = 0; iterator.hasNext(); ++i) {
      if (i > 0)
        builder.append(",\n");

      builder.append(iterator.next());
    }

    return builder.toString();
  }

  /**
   * Recursively process each sub-path of the specified directory.
   *
   * @param dir The directory to process.
   * @param predicate The predicate defining the test process.
   * @return {@code true} if the specified predicate returned {@code true} for
   *         each sub-path to which it was applied, otherwise {@code false}.
   */
  public static boolean recurseDir(final File dir, final Predicate<File> predicate) {
    final File[] files = dir.listFiles();
    if (files != null)
      for (final File file : files)
        if (!recurseDir(file, predicate))
          return false;

    return predicate.test(dir);
  }

  /**
   * Recursively process each sub-path of the specified directory.
   *
   * @param dir The directory to process.
   * @param function The function defining the test process, which returns a
   *          {@link FileVisitResult} to direct the recursion process.
   * @return A {@link FileVisitResult} to direct the recursion process.
   */
  public static FileVisitResult recurseDir(final File dir, final Function<File,FileVisitResult> function) {
    final File[] files = dir.listFiles();
    if (files != null) {
      for (final File file : files) {
        final FileVisitResult result = recurseDir(file, function);
        if (result == FileVisitResult.SKIP_SIBLINGS)
          break;

        if (result == FileVisitResult.TERMINATE)
          return result;

        if (result == FileVisitResult.SKIP_SUBTREE)
          return FileVisitResult.SKIP_SIBLINGS;
      }
    }

    return function.apply(dir);
  }

  /**
   * Compares two {@code Object} arrays, within comparable elements,
   * lexicographically.
   * <p>
   * A {@code null} array reference is considered lexicographically less than a
   * non-{@code null} array reference. Two {@code null} array references are
   * considered equal. A {@code null} array element is considered
   * lexicographically than a non-{@code null} array element. Two {@code null}
   * array elements are considered equal.
   * <p>
   * The comparison is consistent with {@link Arrays#equals(Object[], Object[])
   * equals}, more specifically the following holds for arrays {@code a} and
   * {@code b}:
   *
   * <pre>
   * {@code Arrays.equals(a, b) == (Arrays.compare(a, b) == 0)}
   * </pre>
   *
   * @param a The first array to compare.
   * @param b The second array to compare.
   * @param <T> The type of comparable array elements.
   * @return The value {@code 0} if the first and second array are equal and
   *         contain the same elements in the same order; a value less than
   *         {@code 0} if the first array is lexicographically less than the
   *         second array; and a value greater than {@code 0} if the first array
   *         is lexicographically greater than the second array.
   */
  public static <T extends Comparable<? super T>>int compare(final T[] a, final T[] b) {
    if (a == b)
      return 0;

    // A null array is less than a non-null array
    if (a == null || b == null)
      return a == null ? -1 : 1;

    int length = Math.min(a.length, b.length);
    for (int i = 0; i < length; i++) {
      final T oa = a[i];
      final T ob = b[i];
      if (oa != ob) {
        // A null element is less than a non-null element
        if (oa == null || ob == null)
          return oa == null ? -1 : 1;

        final int v = oa.compareTo(ob);
        if (v != 0)
          return v;
      }
    }

    return a.length - b.length;
  }

  /**
   * Tests whether the first specified array contains all {@link Comparable}
   * elements in the second specified array.
   *
   * @param <T> Type parameter of array, which must extend {@link Comparable}.
   * @param a The first specified array (sorted).
   * @param b The second specified array (sorted).
   * @return {@code true} if the first specifies array contains all elements in
   *         the second specified array.
   * @throws NullPointerException If {@code a} or {@code b} are null.
   */
  public static <T extends Comparable<T>>boolean containsAll(final T[] a, final T[] b) {
    for (int i = 0, j = 0;;) {
      if (j == b.length)
        return true;

      if (i == a.length)
        return false;

      final int comparison = a[i].compareTo(b[j]);
      if (comparison > 0)
        return false;

      ++i;
      if (comparison == 0)
        ++j;
    }
  }

  /**
   * Tests whether the first specifies array contains all elements in the second
   * specified array, with comparison determined by the specified
   * {@link Comparator}.
   *
   * @param <T> Type parameter of array.
   * @param a The first specified array (sorted).
   * @param b The second specified array (sorted).
   * @param c The {@link Comparator}.
   * @return {@code true} if the first specifies array contains all elements in
   *         the second specified array.
   * @throws NullPointerException If {@code a} or {@code b} are null.
   */
  public static <T>boolean containsAll(final T[] a, final T[] b, final Comparator<T> c) {
    for (int i = 0, j = 0;;) {
      if (j == b.length)
        return true;

      if (i == a.length)
        return false;

      final int comparison = c.compare(a[i], b[j]);
      if (comparison > 0)
        return false;

      ++i;
      if (comparison == 0)
        ++j;
    }
  }

  /**
   * Returns an array of type {@code <T>} that includes only the elements that
   * belong to the specified arrays (the specified arrays must be sorted).
   * <p>
   * <i><b>Note:</b> This is a recursive algorithm, implemented to take
   * advantage of the high performance of callstack registers, but will fail due
   * to a {@link StackOverflowError} if the number of differences between the
   * first and second specified arrays approaches ~8000.</i>
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
  public static <T extends Comparable<T>>T[] retain(final T[] a, final T[] b, final int i, final int j, final int r) {
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

  /**
   * Sorts the specified array of objects into ascending order, according to the
   * natural ordering of its elements. All elements in the array must implement
   * the {@link Comparable} interface. Furthermore, all elements in the array
   * must be mutually comparable (that is, {@code e1.compareTo(e2)} must not
   * throw a {@link ClassCastException} for any elements {@code e1} and
   * {@code e2} in the array).
   *
   * @param <T> The component type of the specified array.
   * @param array The array to be sorted.
   * @return The specified array, which is sorted in-place (unless it is null).
   * @see Arrays#sort(Object[])
   */
  public static <T>T[] sort(final T[] array) {
    if (array == null)
      return null;

    Arrays.sort(array);
    return array;
  }

  /**
   * Returns the name of the class of the specified object suffixed with
   * {@code '@'} followed by the hexadecimal representation of the object's
   * identity hash code, or {@code "null"} if the specified object is null.
   *
   * @param obj The object.
   * @return The name of the class of the specified object suffixed with
   *         {@code '@'} followed by the hexadecimal representation of the
   *         object's identity hash code, or {@code "null"} if the specified
   *         object is null.
   * @see #getSimpleNameId(Object)
   */
  public static String getNameId(final Object obj) {
    return obj != null ? obj.getClass().getName() + "@" + Integer.toString(System.identityHashCode(obj), 16) : "null";
  }

  /**
   * Returns the simple name of the class of the specified object suffixed with
   * {@code '@'} followed by the hexadecimal representation of the object's
   * identity hash code, or {@code "null"} if the specified object is null.
   *
   * @param obj The object.
   * @return The simple name of the class of the specified object suffixed with
   *         {@code '@'} followed by the hexadecimal representation of the
   *         object's identity hash code, or {@code "null"} if the specified
   *         object is null.
   * @see #getNameId(Object)
   */
  public static String getSimpleNameId(final Object obj) {
    return obj != null ? obj.getClass().getSimpleName() + "@" + Integer.toString(System.identityHashCode(obj), 16) : "null";
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
  public static String toString(final Object[] a, final String del) {
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
   * Returns a string representation of the specified collection, using the
   * specified delimiter between the string representation of each element. If
   * the specified collection is null, this method returns the string
   * {@code "null"}. If the size of the specified collection is 0, this method
   * returns {@code ""}.
   *
   * @param c The array.
   * @param del The delimiter.
   * @return A string representation of the specified array, using the specified
   *         delimiter between the string representation of each element.
   */
  public static String toString(final Collection<?> c, final String del) {
    if (c == null)
      return "null";

    if (c.size() == 0)
      return "";

    final StringBuilder builder = new StringBuilder();
    final Iterator<?> iterator = c.iterator();
    for (int i = 0; iterator.hasNext(); ++i) {
      if (i > 0)
        builder.append(del);

      builder.append(String.valueOf(iterator.next()));
    }

    return builder.toString();
  }

  public static void absorbProperties(final String command) {
    final String[] parts = command.split("\\s+-");
    for (int i = 0; i < parts.length; ++i) {
      final String part = parts[i];
      if (part.charAt(0) != 'D')
        continue;

      final int index = part.indexOf('=');
      if (index == -1)
        System.setProperty(part.substring(1), "");
      else
        System.setProperty(part.substring(1, index), part.substring(index + 1));
    }
  }

  public static void forEachClass(final URL[] urls, final Consumer<String> consumer) throws IOException {
    for (final URL url : urls) {
      if (url.getPath().endsWith(".jar")) {
        try (final ZipInputStream in = new ZipInputStream(url.openStream())) {
          for (ZipEntry entry; (entry = in.getNextEntry()) != null;) {
            final String name = entry.getName();
            if (name.endsWith(".class") && !name.startsWith("META-INF/") && !name.startsWith("module-info")) {
              consumer.accept(name);
            }
          }
        }
      }
      else {
        final File file = new File(url.getPath());
        final Path path = file.toPath();
        AssembleUtil.recurseDir(file, new Predicate<File>() {
          @Override
          public boolean test(final File t) {
            if (t.isDirectory())
              return true;

            final String name = path.relativize(t.toPath()).toString();
            if (name.endsWith(".class") && !name.startsWith("META-INF/") && !name.startsWith("module-info")) {
              consumer.accept(name);
            }

            return true;
          }
        });
      }
    }
  }

  private static URL _toURL(final File file) throws MalformedURLException {
    final String path = file.getAbsolutePath();
    return new URL("file", "", file.isDirectory() ? path + "/" : path);
  }

  public static URL toURL(final File file) {
    try {
      return _toURL(file);
    }
    catch (final MalformedURLException e) {
      throw new IllegalStateException(e);
    }
  }

  public static URL[] toURLs(final File ... files) {
    try {
      final URL[] urls = new URL[files.length];
      for (int i = 0; i < files.length; ++i)
        urls[i] = _toURL(files[i]);

      return urls;
    }
    catch (final MalformedURLException e) {
      throw new IllegalStateException(e);
    }
  }

  public static URL[] toURLs(final List<File> files) {
    try {
      final URL[] urls = new URL[files.size()];
      for (int i = 0; i < files.size(); ++i)
        urls[i] = _toURL(files.get(i));

      return urls;
    }
    catch (final MalformedURLException e) {
      throw new IllegalStateException(e);
    }
  }

  public static URL[] toURLs(final Collection<File> files) {
    try {
      final URL[] urls = new URL[files.size()];
      final Iterator<File> iterator = files.iterator();
      for (int i = 0; iterator.hasNext(); ++i)
        urls[i] = AssembleUtil._toURL(iterator.next());

      return urls;
    }
    catch (final MalformedURLException e) {
      throw new IllegalStateException(e);
    }
  }

  private AssembleUtil() {
  }
}