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
import java.lang.management.ManagementFactory;
import java.net.JarURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.CodeSource;
import java.util.Arrays;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;

import io.opentracing.contrib.specialagent.Manager.Event;

/**
 * Utility functions for the SpecialAgent.
 *
 * @author Seva Safris
 */
public final class SpecialAgentUtil {
  private static final Logger logger = Logger.getLogger(SpecialAgentUtil.class.getName());

  static JarFile createTempJarFile(final File dir) throws IOException {
    final Path dirPath = dir.toPath();
    final Path zipPath = Files.createTempFile("specialagent", ".jar");
    try (
      final FileOutputStream fos = new FileOutputStream(zipPath.toFile());
      final JarOutputStream jos = new JarOutputStream(fos);
    ) {
      AssembleUtil.recurseDir(dir, new Predicate<File>() {
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

  static String getInputArguments() {
    final StringBuilder builder = new StringBuilder();
    final Iterator<String> iterator = ManagementFactory.getRuntimeMXBean().getInputArguments().iterator();
    for (int i = 0; iterator.hasNext(); ++i) {
      if (i > 0)
        builder.append(' ');

      builder.append(iterator.next());
    }

    return builder.toString();
  }

  private static URL getLocation(final Class<?> cls) {
    final CodeSource codeSource = cls.getProtectionDomain().getCodeSource();
    if (codeSource != null)
      return codeSource.getLocation();

    for (final String arg : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
      if (arg.startsWith("-javaagent:")) {
        try {
          return new URL("file", null, arg.substring(11));
        }
        catch (final MalformedURLException e) {
          throw new IllegalStateException(e);
        }
      }
    }

    final String sunJavaCommand = System.getProperty("sun.java.command");
    if (sunJavaCommand == null)
      return null;

    final String[] parts = sunJavaCommand.split("\\s+-");
    for (int i = 0; i < parts.length; ++i) {
      final String part = parts[i];
      if (part.startsWith("javaagent:")) {
        try {
          return new URL("file", null, part.substring(10));
        }
        catch (final MalformedURLException e) {
          throw new IllegalStateException(e);
        }
      }
    }

    return null;
  }

  private static Manifest getManifest(final URL location) throws IOException {
    try (final JarInputStream in = new JarInputStream(location.openStream())) {
      return in.getManifest();
    }
  }

  private static String getBootClassPathManifestEntry(final URL location) throws IOException {
    final Manifest manifest = SpecialAgentUtil.getManifest(location);
    if (manifest == null)
      return null;

    final Attributes attributes = manifest.getMainAttributes();
    if (attributes == null)
      return null;

    return attributes.getValue("Boot-Class-Path");
  }

  /**
   * Asserts that the name of the JAR used on the command line matches the name
   * for the "Boot-Class-Path" entry in META-INF/MANIFEST.MF.
   *
   * @throws IllegalStateException If the name is not what is expected.
   */
  static void assertJavaAgentJarName() {
    try {
      final URL location = getLocation(SpecialAgent.class);
      if (location == null) {
        logger.fine("Running from IDE? Could not find " + JarFile.MANIFEST_NAME);
      }
      else {
        final String bootClassPathManifestEntry = getBootClassPathManifestEntry(location);
        if (bootClassPathManifestEntry == null) {
          if (logger.isLoggable(Level.FINE))
            logger.fine("Running from IDE? Could not find " + JarFile.MANIFEST_NAME);
        }
        else {
          final String jarName = getName(location.toString());
          if (!jarName.equals(bootClassPathManifestEntry))
            throw new IllegalStateException("Name of -javaagent JAR, which is currently " + jarName + ", must be: " + bootClassPathManifestEntry);
        }
      }
    }
    catch (final IOException e) {
      logger.log(Level.WARNING, e.getMessage(), e);
    }
  }

  /**
   * @return A {@code Set} of strings representing the paths in classpath of the
   *         current process.
   */
  static Set<String> getJavaClassPath() {
    return new LinkedHashSet<>(Arrays.asList(System.getProperty("java.class.path").split(File.pathSeparator)));
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
  static File getSourceLocation(final URL url, final String resourcePath) throws MalformedURLException {
    final String string = url.toString();
    if (!string.endsWith(resourcePath))
      throw new IllegalArgumentException(url + " does not end with \"" + resourcePath + "\"");

    if (string.startsWith("jar:file:"))
      return new File(string.substring(9, string.lastIndexOf('!')));

    if (string.startsWith("file:"))
      return new File(string.substring(5, string.length() - resourcePath.length()));

    throw new UnsupportedOperationException("Unsupported protocol: " + url.getProtocol());
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

  public static URL[] toURLs(final Collection<File> files) {
    try {
      final URL[] urls = new URL[files.size()];
      final Iterator<File> iterator = files.iterator();
      for (int i = 0; iterator.hasNext(); ++i)
        urls[i] = iterator.next().toURI().toURL();

      return urls;
    }
    catch (final MalformedURLException e) {
      throw new IllegalStateException(e);
    }
  }

  public static URL[] toURLs(final File ... files) {
    try {
      final URL[] urls = new URL[files.length];
      for (int i = 0; i < files.length; ++i)
        urls[i] = files[i].toURI().toURL();

      return urls;
    }
    catch (final MalformedURLException e) {
      throw new IllegalStateException(e);
    }
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
  public static File[] classPathToFiles(final String classpath) {
    if (classpath == null)
      return null;

    final String[] paths = classpath.split(File.pathSeparator);
    final File[] files = new File[paths.length];
    for (int i = 0; i < paths.length; ++i)
      files[i] = new File(paths[i]);

    return files;
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
  static String getName(final String path) {
    if (path.length() == 0)
      throw new IllegalArgumentException("Empty path");

    if (path.length() == 0)
      return path;

    final boolean end = path.charAt(path.length() - 1) == '/';
    final int start = end ? path.lastIndexOf('/', path.length() - 2) : path.lastIndexOf('/');
    return start == -1 ? (end ? path.substring(0, path.length() - 1) : path) : end ? path.substring(start + 1, path.length() - 1) : path.substring(start + 1);
  }

  /**
   * Fills the specified {@code fileToPluginManifest} map with JAR files having
   * a prefix path that match {@code path}, and the associated
   * {@link PluginManifest}.
   * <p>
   * This method will add a shutdown hook to delete any temporary directory and
   * file resources it created.
   *
   * @param path The prefix path to match when finding resources.
   * @param instruPluginNameToEnable Map of instrumentation plugin name to
   *          boolean specifying whether it should be included in the runtime.
   * @param tracerPluginNameToEnable Map of tracer plugin name to boolean
   *          specifying whether it should be included in the runtime.
   * @param fileToPluginManifest The {@code Map} to be filled with JAR files
   *          having a prefix path that match {@code path}, and the associated
   *          {@link PluginManifest}.
   * @throws IllegalStateException If an illegal state occurs due to an
   *           {@link IOException}.
   */
  static void findJarResources(final String path, final Map<String,Boolean> instruPluginNameToEnable, final Map<String,Boolean> tracerPluginNameToEnable, final Map<File,PluginManifest> fileToPluginManifest) {
    try {
      final Enumeration<URL> resources = ClassLoader.getSystemClassLoader().getResources(path);
      if (!resources.hasMoreElements())
        return;

      final boolean allInstruEnabled = !instruPluginNameToEnable.containsKey(null) || instruPluginNameToEnable.remove(null);
      if (logger.isLoggable(Level.FINER))
        logger.finer("Instrumentation Plugins are " + (allInstruEnabled ? "en" : "dis") + "abled by default");

      final boolean allTracerEnabled = !tracerPluginNameToEnable.containsKey(null) || tracerPluginNameToEnable.remove(null);
      if (logger.isLoggable(Level.FINER))
        logger.finer("Tracer Plugins are " + (allTracerEnabled ? "en" : "dis") + "abled by default");

      final Set<URL> visitedResources = new HashSet<>();
      File destDir = null;
      do {
        final URL resource = resources.nextElement();
        if (visitedResources.contains(resource))
          continue;

        visitedResources.add(resource);
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
        final Enumeration<JarEntry> jarEntries = jarFile.entries();
        while (jarEntries.hasMoreElements()) {
          final String jarEntry = jarEntries.nextElement().getName();
          if (jarEntry.length() <= path.length() || !jarEntry.startsWith(path))
            continue;

          final int slash = jarEntry.lastIndexOf('/');
          final String jarFileName = jarEntry.substring(slash + 1);

          // First, extract the JAR into a temp dir
          final File subDir = new File(destDir, jarEntry.substring(0, slash));
          subDir.mkdirs();
          final File file = new File(subDir, jarFileName);
          if (!file.isDirectory() && !file.getName().endsWith(".jar"))
            continue;

          final URL jarUrl = new URL(resource, jarEntry.substring(path.length()));
          Files.copy(jarUrl.openStream(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);

          // Then, identify whether the JAR is an Instrumentation or Tracer Plugin
          final PluginManifest pluginManifest = PluginManifest.getPluginManifest(file);
          boolean enablePlugin = true;
          if (pluginManifest != null) {
            final boolean isInstruPlugin = pluginManifest.type == PluginManifest.Type.INSTRUMENTATION;
            // Next, see if it is included or excluded
            enablePlugin = isInstruPlugin ? allInstruEnabled : allTracerEnabled;
            final Map<String,Boolean> pluginNameToEnable = isInstruPlugin ? instruPluginNameToEnable : tracerPluginNameToEnable;
            for (final Map.Entry<String,Boolean> entry : pluginNameToEnable.entrySet()) {
              final String pluginName = entry.getKey();
              if (pluginName.equals(pluginManifest.name)) {
                enablePlugin = entry.getValue();
                if (logger.isLoggable(Level.FINER))
                  logger.finer((isInstruPlugin ? "Instrumentation" : "Tracer") + " Plugin " + pluginName + " is " + (enablePlugin ? "en" : "dis") + "abled");

                break;
              }
            }
          }

          if (enablePlugin) {
            fileToPluginManifest.put(file, pluginManifest);
          }
          else {
            file.delete();
          }
        }
      }
      while (resources.hasMoreElements());

      if (destDir != null) {
        final File targetDir = destDir;
        Runtime.getRuntime().addShutdownHook(new Thread() {
          @Override
          public void run() {
            AssembleUtil.recurseDir(targetDir, new Predicate<File>() {
              @Override
              public boolean test(final File t) {
                return t.delete();
              }
            });
          }
        });
      }
    }
    catch (final IOException e) {
      throw new IllegalStateException(e);
    }
  }

  /**
   * Returns the name of the specified {@code Class} as per the following rules:
   * <ul>
   * <li>If {@code cls} represents {@code void}, this method returns
   * {@code null}</li>
   * <li>If {@code cls} represents an array, this method returns the code
   * semantics representation (i.e. {@code java.lang.Object[]})</li>
   * <li>Otherwise, this method return {@code cls.getName()}</li>
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

  private SpecialAgentUtil() {
  }
}