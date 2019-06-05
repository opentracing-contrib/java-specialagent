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
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.FileVisitResult;
import java.util.Arrays;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

public final class AssembleUtil {
  private static final int DEFAULT_SOCKET_BUFFER_SIZE = 65536;
  private static final String[] scopes = {"compile", "provided", "runtime", "system", "test"};

  /**
   * Filters the specified array of URL objects by checking if the file name of
   * the URL is included in the specified {@code Set} of string names.
   *
   * @param files The array of URL objects to filter.
   * @param matches The set of {@code File} objects whose names are to be matched
   *          by the specified array of URL objects.
   * @param index The index value for stack tracking (must be called with 0).
   * @param depth The depth value for stack tracking (must be called with 0).
   * @return An array of {@code URL} objects that have file names that belong to
   *         the specified {@code Set} of string names.
   * @throws MalformedURLException If a parsed URL fails to comply with the
   *           specific syntax of the associated protocol.
   */
  private static File[] filterUrlFileNames(final File[] files, final Set<File> matches, final int index, final int depth) throws MalformedURLException {
    for (int i = index; i < files.length; ++i) {
      final File file = files[i];
      final String artifact;
      if (file.isDirectory() && "target".equals(file.getParentFile().getName()) && "classes".equals(file.getName()))
        artifact = getArtifactFile(file.getParentFile().getParentFile());
      else if (file.isFile() && file.getName().endsWith(".jar"))
        artifact = file.getName();
      else
        continue;

      for (final File match : matches) {
        if (artifact.equals(match.getName())) {
          final File[] results = filterUrlFileNames(files, matches, i + 1, depth + 1);
          results[depth] = file;
          return results;
        }
      }
    }

    return depth == 0 ? null : new File[depth];
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
   * @param files The array of File objects to filter.
   * @param dependenciesTgf The contents of the TGF file that specify the
   *          dependencies.
   * @param includeOptional Whether to include dependencies marked as
   *          {@code (optional)}.
   * @param scopes An array of Maven scopes to include in the returned set, or
   *          {@code null} to include all scopes.
   * @return An array of URL objects representing Instrumentation Rule URLs
   * @throws IOException If an I/O error has occurred.
   */
  public static File[] filterRuleURLs(final File[] files, final String dependenciesTgf, final boolean includeOptional, final String ... scopes) throws IOException {
    final Set<File> matches = selectFromTgf(dependenciesTgf, includeOptional, scopes);
    return filterUrlFileNames(files, matches, 0, 0);
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

  static File getFileForDependency(final String dependency, final String ... scopes) {
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
  static Set<File> selectFromTgf(final String tgf, final boolean includeOptional, final String[] scopes, final Class<?> ... excludes) throws IOException {
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

  private static ZipEntry getEntryFromJar(final ZipFile zipFile, final String name) throws IOException {
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
  static Set<String> getLocations(final Class<?> ... classes) throws IOException {
    final ClassLoader classLoader = ClassLoader.getSystemClassLoader();
    final Set<String> locations = new LinkedHashSet<>();
    for (final Class<?> cls : classes) {
      final String resourceName = cls.getName().replace('.', '/').concat(".class");
      final Enumeration<URL> urls = classLoader.getResources(resourceName);
      while (urls.hasMoreElements()) {
        final String path = urls.nextElement().getFile();
        locations.add(path.startsWith("file:") ? path.substring(5, path.indexOf('!')) : path.substring(0, path.length() - resourceName.length() - 1));
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
  static String toIndentedString(final Collection<?> l) {
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
   * @param predicate The predicate defining the test process.
   * @return {@code true} if the specified predicate returned {@code true} for
   *         each sub-path to which it was applied, otherwise {@code false}.
   */
  public static FileVisitResult recurseDir(final File dir, final Function<File,FileVisitResult> predicate) {
    final File[] files = dir.listFiles();
    if (files != null) {
      for (final File file : files) {
        final FileVisitResult result = recurseDir(file, predicate);
        if (result == FileVisitResult.SKIP_SIBLINGS)
          break;

        if (result == FileVisitResult.TERMINATE)
          return result;

        if (result == FileVisitResult.SKIP_SUBTREE)
          return FileVisitResult.SKIP_SIBLINGS;
      }
    }

    return predicate.apply(dir);
  }

  private AssembleUtil() {
  }
}