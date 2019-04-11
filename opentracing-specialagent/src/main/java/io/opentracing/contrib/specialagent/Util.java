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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.JarURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;

import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

import io.opentracing.contrib.specialagent.Manager.Event;

/**
 * Utility functions for the SpecialAgent.
 *
 * @author Seva Safris
 */
public final class Util {
  private static final Logger logger = Logger.getLogger(Util.class.getName());
  private static final int DEFAULT_SOCKET_BUFFER_SIZE = 65536;
  private static final String[] scopes = {"compile", "provided", "runtime", "system", "test"};

  /**
   * Filters the specified array of URL objects by checking if the file name of
   * the URL is included in the specified {@code Set} of string names.
   *
   * @param urls The array of URL objects to filter.
   * @param names The {@code Set} of string names to be matched by the specified
   *          array of URL objects.
   * @param index The index value for stack tracking (must be called with 0).
   * @param depth The depth value for stack tracking (must be called with 0).
   * @return An array of {@code URL} objects that have file names that belong to
   *         the specified {@code Set} of string names.
   * @throws MalformedURLException If a parsed URL fails to comply with the
   *           specific syntax of the associated protocol.
   */
  private static URL[] filterUrlFileNames(final URL[] urls, final Set<String> names, final int index, final int depth) throws MalformedURLException {
    for (int i = index; i < urls.length; ++i) {
      final String string = urls[i].toString();
      final String artifact;
      if (string.endsWith("/target/classes/"))
        artifact = getArtifactFile(new File(string.substring(5, string.length() - 16)));
      else if (string.endsWith(".jar"))
        artifact = string.substring(string.lastIndexOf('/') + 1);
      else
        continue;

      final boolean match = names.contains(artifact);
      if (match) {
        final URL result = new URL(string);
        final URL[] results = filterUrlFileNames(urls, names, i + 1, depth + 1);
        results[depth] = result;
        return results;
      }
    }

    return depth == 0 ? null : new URL[depth];
  }

  private static String getArtifactFile(final File dir) {
    try {
      final MavenXpp3Reader reader = new MavenXpp3Reader();
      final Model model = reader.read(new FileReader(new File(dir, "pom.xml")));
      final String version = model.getVersion() != null ? model.getVersion() : model.getParent().getVersion();
      return model.getArtifactId() + "-" + version + ".jar";
    }
    catch (final IOException | XmlPullParserException e) {
      throw new IllegalStateException(e);
    }
  }

  /**
   * Filter the specified array of URL objects to return the Instrumentation
   * Rule URLs as specified by the Dependency TGF file at {@code dependencyUrl}.
   *
   * @param urls The array of URL objects to filter.
   * @param dependenciesTgf The contents of the TGF file that specify the
   *          dependencies.
   * @param includeOptional Whether to include dependencies marked as
   *          {@code (optional)}.
   * @param scopes An array of Maven scopes to include in the returned set, or
   *          {@code null} to include all scopes.
   * @return An array of URL objects representing Instrumentation Rule URLs
   * @throws IOException If an I/O error has occurred.
   */
  public static URL[] filterRuleURLs(final URL[] urls, final String dependenciesTgf, final boolean includeOptional, final String ... scopes) throws IOException {
    final Set<String> names = Util.selectFromTgf(dependenciesTgf, includeOptional, scopes);
    return filterUrlFileNames(urls, names, 0, 0);
  }

  public static JarFile createTempJarFile(final File dir) throws IOException {
    final Path dirPath = dir.toPath();
    final Path zipPath = Files.createTempFile("specialagent", ".jar");
    try (
      final FileOutputStream fos = new FileOutputStream(zipPath.toFile());
      final JarOutputStream jos = new JarOutputStream(fos);
    ) {
      recurseDir(dir, new Predicate<File>() {
        @Override
        public boolean test(final File t) {
          if (t.isFile()) {
            final Path filePath = t.toPath();
            final String name = dirPath.relativize(filePath).toString();
            try {
              jos.putNextEntry(new ZipEntry(name));
              jos.write(Files.readAllBytes(filePath));
              jos.closeEntry();
            }
            catch (final IOException e) {
              throw new IllegalStateException(e);
            }
          }

          return true;
        }
      });
    }

    final File file = zipPath.toFile();
    file.deleteOnExit();
    return new JarFile(file);
  }

  /**
   * Tests if the specified string is a name of a Maven scope.
   *
   * @param scope The string to test.
   * @return {@code true} if the specified string is a name of a Maven scope.
   */
  private static boolean isScope(final String scope) {
    return Arrays.binarySearch(scopes, scope) > -1;
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
  static Set<String> selectFromTgf(final String tgf, final boolean includeOptional, final String[] scopes, final Class<?> ... excludes) throws IOException {
    final Set<String> excluded = getLocations(excludes);
    final Set<String> paths = new HashSet<>();
    final StringTokenizer tokenizer = new StringTokenizer(tgf, "\r\n");
    TOKENIZER:
    while (tokenizer.hasMoreTokens()) {
      String token = tokenizer.nextToken().trim();
      if ("#".equals(token))
        break;

      final boolean isOptional = token.endsWith("(optional)");
      if (isOptional) {
        if (!includeOptional)
          continue;

        token = token.substring(0, token.length() - 11);
      }

      // artifactId
      final int c0 = token.indexOf(':');
      final int c1 = token.indexOf(':', c0 + 1);
      final String artifactId = token.substring(c0 + 1, c1);

      // type
      final int c2 = token.indexOf(':', c1 + 1);
      final String type = token.substring(c1 + 1, c2);

      // classifier or version
      final int c3 = token.indexOf(':', c2 + 1);
      final String classifierOrVersion = token.substring(c2 + 1, c3 > c2 ? c3 : token.length());

      // version or scope
      final int c4 = c3 == -1 ? -1 : token.indexOf(':', c3 + 1);
      final String versionOrScope = c3 == -1 ? null : token.substring(c3 + 1, c4 > c3 ? c4 : token.length());

      final String scope = c4 == -1 ? null : token.substring(c4 + 1);

      final String fileName;
      if (scope != null) {
        if (scopes != null && !Arrays.asList(scopes).contains(scope))
          continue;

        fileName = artifactId + "-" + versionOrScope + "-" + classifierOrVersion + "." + type.replace("test-", "");
      }
      else if (versionOrScope != null) {
        final boolean hasScope = isScope(versionOrScope);
        if (scopes != null && (hasScope ? !Arrays.asList(scopes).contains(versionOrScope) : !Arrays.asList(scopes).contains("compile")))
          continue;

        fileName = artifactId + "-" + (!hasScope ? versionOrScope + "-" + classifierOrVersion : classifierOrVersion) + "." + type.replace("test-", "");
      }
      else {
        if (scopes != null && !Arrays.asList(scopes).contains("compile"))
          continue;

        fileName = artifactId + "-" + classifierOrVersion + "." + type.replace("test-", "");
      }

      for (final String exclude : excluded)
        if (exclude.endsWith(fileName))
          continue TOKENIZER;

      paths.add(fileName);
    }

    return paths;
  }

  /**
   * @return A {@code Set} of strings representing the paths in classpath of the
   *         current process.
   */
  static Set<String> getJavaClassPath() {
    return new LinkedHashSet<>(Arrays.asList(System.getProperty("java.class.path").split(File.pathSeparator)));
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
  static Set<String> getLocations(final Class<?> ... classes) throws IOException {
    final ClassLoader classLoader = ClassLoader.getSystemClassLoader();
    final Set<String> excludes = new LinkedHashSet<>();
    for (final Class<?> cls : classes) {
      final String resourceName = cls.getName().replace('.', '/').concat(".class");
      final Enumeration<URL> urls = classLoader.getResources(resourceName);
      while (urls.hasMoreElements()) {
        final String path = urls.nextElement().getFile();
        excludes.add(path.startsWith("file:") ? path.substring(5, path.indexOf('!')) : path.substring(0, path.length() - resourceName.length() - 1));
      }
    }

    return excludes;
  }

  /**
   * Returns the source location of the specified resource in the specified URL.
   *
   * @param url The {@code URL} from which to find the source location.
   * @param resourcePath The resource path that is the suffix of the specified
   *          URL.
   * @return The source location of the specified resource in the specified URL.
   * @throws MalformedURLException If no protocol is specified, or an unknown
   *           protocol is found, or spec is null.
   * @throws IllegalArgumentException If the specified resource path is not the
   *           suffix of the specified URL.
   */
  static URL getSourceLocation(final URL url, final String resourcePath) throws MalformedURLException {
    final String string = url.toString();
    if (!string.endsWith(resourcePath))
      throw new IllegalArgumentException(url + " does not end with \"" + resourcePath + "\"");

    return new URL(string.startsWith("jar:") ? string.substring(4, string.lastIndexOf('!')) : string.substring(0, string.length() - resourcePath.length()));
  }

  /**
   * Returns the array of bytes read from the specified {@code URL}.
   *
   * @param url The URL from which to read bytes.
   * @return The array of bytes read from an {@code InputStream}.
   */
  public static byte[] readBytes(final URL url) {
    try {
      try (final InputStream in = url.openStream()) {
        final ByteArrayOutputStream buffer = new ByteArrayOutputStream(DEFAULT_SOCKET_BUFFER_SIZE);
        final byte[] bytes = new byte[DEFAULT_SOCKET_BUFFER_SIZE];
        for (int len; (len = in.read(bytes)) != -1;)
          if (len != 0)
            buffer.write(bytes, 0, len);

        return buffer.toByteArray();
      }
    }
    catch (final IOException e) {
      throw new IllegalStateException(e);
    }
  }

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
        builder.append(",\n");

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
  public static URL[] classPathToURLs(final String classpath) {
    if (classpath == null)
      return null;

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
   * Returns the hexadecimal representation of an object's identity hash code.
   *
   * @param obj The object.
   * @return The hexadecimal representation of an object's identity hash code.
   */
  static String getIdentityCode(final Object obj) {
    return obj == null ? "null" : obj.getClass().getName() + "@" + Integer.toString(System.identityHashCode(obj), 16);
  }

  /**
   * Returns the name of the file or directory denoted by the specified
   * pathname. This is just the last name in the name sequence of {@code path}.
   * If the name sequence of {@code path} is empty, then the empty string is
   * returned.
   *
   * @param path The path string.
   * @return The name of the file or directory denoted by the specified
   *         pathname, or the empty string if the name sequence of {@code path}
   *         is empty.
   * @throws NullPointerException If {@code path} is null.
   * @throws IllegalArgumentException If {@code path} is an empty string.
   */
  public static String getName(final String path) {
    if (path.length() == 0)
      throw new IllegalArgumentException("Empty path");

    if (path.length() == 0)
      return path;

    final boolean end = path.charAt(path.length() - 1) == '/';
    final int start = end ? path.lastIndexOf('/', path.length() - 2) : path.lastIndexOf('/');
    return start == -1 ? (end ? path.substring(0, path.length() - 1) : path) : end ? path.substring(start + 1, path.length() - 1) : path.substring(start + 1);
  }

  /**
   * Returns a {@code List} of {@code URL} objects having a prefix path that
   * matches {@code path}. This method will add a shutdown hook to delete any
   * temporary directory and file resources it created.
   *
   * @param path The prefix path to match when finding resources.
   * @param excludes A set of rule names to exclude.
   * @return A {@code List} of {@code URL} objects having a prefix path that
   *         matches {@code path}.
   * @throws IllegalStateException If an illegal state occurs due to an
   *           {@link IOException}.
   */
  static Set<URL> findJarResources(final String path, final Collection<String> excludes) {
    try {
      final Enumeration<URL> resources = ClassLoader.getSystemClassLoader().getResources(path);
      final Set<URL> urls = new HashSet<>();
      if (!resources.hasMoreElements())
        return urls;

      File destDir = null;
      do {
        final URL resource = resources.nextElement();
        final URLConnection connection = resource.openConnection();
        // Only consider resources that are inside JARs
        if (!(connection instanceof JarURLConnection))
          continue;

        if (logger.isLoggable(Level.FINEST))
          logger.finest("SpecialAgent Rule Path: " + resource);

        if (destDir == null)
          destDir = Files.createTempDirectory("opentracing-specialagent").toFile();

        final JarURLConnection jarURLConnection = (JarURLConnection)connection;
        jarURLConnection.setUseCaches(false);
        final JarFile jarFile = jarURLConnection.getJarFile();
        final Enumeration<JarEntry> entries = jarFile.entries();
        OUT:
        while (entries.hasMoreElements()) {
          final String entry = entries.nextElement().getName();
          if (entry.length() <= path.length() || !entry.startsWith(path))
            continue;

          final int slash = entry.lastIndexOf('/');
          final String jarFileName = entry.substring(slash + 1);
          for (final String exclude : excludes)
            if (jarFileName.startsWith(exclude + "-"))
              continue OUT;

          final File subDir = new File(destDir, entry.substring(0, slash));
          subDir.mkdirs();
          final File file = new File(subDir, jarFileName);

          final URL url = new URL(resource, entry.substring(path.length()));
          Files.copy(url.openStream(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
          urls.add(file.toURI().toURL());
        }
      }
      while (resources.hasMoreElements());

      if (destDir != null) {
        final File targetDir = destDir;
        Runtime.getRuntime().addShutdownHook(new Thread() {
          @Override
          public void run() {
            recurseDir(targetDir, new Predicate<File>() {
              @Override
              public boolean test(final File t) {
                return t.delete();
              }
            });
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
   * @param <T> The component type of the specified array.
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
  static <T extends Comparable<T>>boolean containsAll(final T[] a, final T[] b) {
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
  static <T>boolean containsAll(final T[] a, final T[] b, final Comparator<T> c) {
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

  private static final Event[] DEFAULT_EVENTS = new Event[5];

  static Event[] digestEventsProperty(final String eventsProperty) {
    if (eventsProperty == null)
      return DEFAULT_EVENTS;

    final String[] parts = eventsProperty.split(",");
    Arrays.sort(parts);
    final Event[] events = Event.values();
    for (int i = 0, j = 0; i < events.length;) {
      final int comparison = j < parts.length ? events[i].name().compareTo(parts[j]) : -1;
      if (comparison < 0) {
        events[i] = null;
        ++i;
      }
      else if (comparison > 0) {
        ++j;
      }
      else {
        ++i;
        ++j;
      }
    }

    return events;
  }

  public static <T>T proxy(final T obj) {
    final ClassLoader targetClassLoader = Thread.currentThread().getContextClassLoader();
    if (targetClassLoader == obj.getClass().getClassLoader())
      return obj;

    try {
      final Class<?>[] interfaces = obj.getClass().getInterfaces();
      for (int i = 0; i < interfaces.length; ++i)
        interfaces[i] = Class.forName(interfaces[i].getName(), false, targetClassLoader);

      final Object o = Proxy.newProxyInstance(targetClassLoader, interfaces, new InvocationHandler() {
        @Override
        public Object invoke(final Object proxy, final Method m, final Object[] args) throws Throwable {
          if (args == null || args.length == 0) {
            System.err.println("0 " + targetClassLoader + " -> " + obj.getClass().getClassLoader());
            return obj.getClass().getMethod(m.getName()).invoke(obj);
          }

          final Class<?>[] types = m.getParameterTypes();
          final Class<?>[] proxyTypes = new Class<?>[types.length];
          for (int i = 0; i < types.length; ++i) {
            final Class<?> type = types[i];
            proxyTypes[i] = type.getClassLoader() == targetClassLoader ? type : Class.forName(type.getName(), false, targetClassLoader);
          }

          final Object[] proxyArgs = new Object[args.length];
          for (int i = 0; i < args.length; ++i) {
            final Object arg = args[i];
            proxyArgs[i] = arg == null || arg.getClass().getClassLoader() == targetClassLoader ? arg : proxy(arg);
          }

          final Method method = obj.getClass().getDeclaredMethod(m.getName(), proxyTypes);
          System.err.println(": " + targetClassLoader + " -> " + obj.getClass().getClassLoader());
          return method.invoke(obj, proxyArgs);
        }
      });

      return (T)o;
    }
    catch (final ClassNotFoundException e) {
      throw new IllegalStateException(e);
    }
  }

  private Util() {
  }
}