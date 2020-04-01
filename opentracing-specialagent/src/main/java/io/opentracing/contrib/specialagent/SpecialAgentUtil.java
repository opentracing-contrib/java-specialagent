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
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;

/**
 * Utility functions for the SpecialAgent.
 *
 * @author Seva Safris
 */
public final class SpecialAgentUtil {
//  private static final Logger logger = Logger.getLogger(SpecialAgentUtil.class);

  static File[] parseConfiguration(final Map<String,String> properties, final List<String> verbosePluginNames, final Map<String,Boolean> integrationRuleNameToEnable, final Map<String,Boolean> traceExporterNameToEnable) {
    File[] includedPlugins = null;
    for (final Map.Entry<String,String> property : properties.entrySet()) {
      final String key = property.getKey();
      final String value = property.getValue();
      if (key.startsWith("sa.integration.")) {
        if (key.indexOf(".verbose", 16) != -1)
          verbosePluginNames.add(key.substring(15, key.length() - 8));
        else if (key.indexOf(".enable", 16) != -1)
          integrationRuleNameToEnable.put(key.substring(15, key.length() - 7), !"false".equals(value));
        else if (key.indexOf(".disable", 16) != -1)
          integrationRuleNameToEnable.put(key.substring(15, key.length() - 8), "false".equals(value));
      }
      else if (key.startsWith("sa.exporter.")) {
        if (key.indexOf(".enable", 13) != -1)
          traceExporterNameToEnable.put(key.substring(12, key.length() - 7), !"false".equals(value));
        else if (key.indexOf(".disable", 13) != -1)
          traceExporterNameToEnable.put(key.substring(12, key.length() - 8), "false".equals(value));
      }
      else if (key.equals("sa.include")) {
        final String[] includePaths = value.split(File.pathSeparator);
        includedPlugins = new File[includePaths.length];
        for (int i = 0; i < includePaths.length; ++i)
          includedPlugins[i] = new File(includePaths[i]);
      }
    }

    return includedPlugins;
  }

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

  static StringBuilder getInputArguments() {
    final StringBuilder builder = new StringBuilder();
    final Iterator<String> iterator = ManagementFactory.getRuntimeMXBean().getInputArguments().iterator();
    for (int i = 0; iterator.hasNext(); ++i) {
      if (i > 0)
        builder.append(' ');

      builder.append(iterator.next());
    }

    return builder;
  }

  private static URL getJavaAgentJar(final String arg) throws MalformedURLException {
    final int argsIndex = arg.indexOf(".jar=");
    return new URL("file", null, argsIndex == -1 ? arg : arg.substring(0, argsIndex + 4));
  }

  private static URL getLocation(final Class<?> cls) {
    final CodeSource codeSource = cls.getProtectionDomain().getCodeSource();
//    if (logger.isLoggable(Level.FINEST))
//      logger.finest(SpecialAgentUtil.class.getSimpleName() + "#getLocation(" + cls.getName() + "): [CodeSource] -> " + (codeSource == null ? null : codeSource.getLocation()));

    if (codeSource != null)
      return codeSource.getLocation();

    for (final String arg : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
      if (arg.startsWith("-javaagent:")) {
        try {
          final URL location = getJavaAgentJar(arg.substring(11));
//          if (logger.isLoggable(Level.FINEST))
//            logger.finest(SpecialAgentUtil.class.getSimpleName() + "#getLocation(" + cls.getName() + "): [MXBean] -> " + location);

          return location;
        }
        catch (final MalformedURLException e) {
          throw new IllegalStateException(e);
        }
      }
    }

    final String sunJavaCommand = System.getProperty("sun.java.command");
    if (sunJavaCommand == null)
      return null;

    final String[] args = sunJavaCommand.split("\\s+-");
    for (int i = 0; i < args.length; ++i) {
      final String arg = args[i];
      if (arg.startsWith("javaagent:")) {
        try {
          final URL location = getJavaAgentJar(arg.substring(10));
//          if (logger.isLoggable(Level.FINEST))
//            logger.finest(SpecialAgentUtil.class.getSimpleName() + "#getLocation(" + cls.getName() + "): [sun.java.command] -> " + location);

          return location;
        }
        catch (final MalformedURLException e) {
          throw new IllegalStateException(e);
        }
      }
    }

    return null;
  }

  private static Manifest getManifest(final URL location) throws IOException {
//    if (logger.isLoggable(Level.FINEST))
//      logger.finest(SpecialAgentUtil.class.getSimpleName() + "#getManifest(\"" + location + "\")");

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
//        if (logger.isLoggable(Level.FINE))
//          logger.fine("Running from IDE? Could not find " + JarFile.MANIFEST_NAME);
      }
      else {
        final String bootClassPathManifestEntry = getBootClassPathManifestEntry(location);
        if (bootClassPathManifestEntry == null) {
//          if (logger.isLoggable(Level.FINE))
//            logger.fine("Running from IDE? Could not find " + JarFile.MANIFEST_NAME);
        }
        else {
          final String jarName = AssembleUtil.getName(location.getPath());
          if (!jarName.equals(bootClassPathManifestEntry))
            throw new IllegalStateException("Name of -javaagent JAR, which is currently " + jarName + ", must be: " + bootClassPathManifestEntry);
        }
      }
    }
    catch (final IOException e) {
//      logger.log(Level.WARNING, e.getMessage(), e);
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
   * Fills the specified {@code fileToPluginManifest} map with JAR files having
   * a prefix path that match {@code path}, and the associated
   * {@link PluginManifest}.
   * <p>
   * This method will add a shutdown hook to delete any temporary directory and
   * file resources it created.
   *
   * @param path The prefix path to match when finding resources.
   * @param destDir Callback that supplies the destDir.
   * @param callback Callback function to process resource files.
   * @throws IllegalStateException If an illegal state occurs due to an
   *           {@link IOException}.
   */
  static void findJarResources(final String path, final Supplier<File> destDir, Predicate<File> callback) {
    try {
      final Enumeration<URL> resources = ClassLoader.getSystemClassLoader().getResources(path);
      if (!resources.hasMoreElements())
        return;

      final Set<URL> visitedResources = new HashSet<>();
      File outDir = null;
      do {
        final URL resource = resources.nextElement();
        if (visitedResources.contains(resource))
          continue;

        visitedResources.add(resource);
        final URLConnection connection = resource.openConnection();
        // Only consider resources that are inside JARs
        if (!(connection instanceof JarURLConnection))
          continue;

//        if (logger.isLoggable(Level.FINEST))
//          logger.finest("SpecialAgent Rule Path: " + resource);

        if (outDir == null)
          outDir = destDir.get();

        if (outDir == null) {
//          logger.severe("Unable to continue with null output directory");
          return;
        }

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
          final File subDir = new File(outDir, jarEntry.substring(0, slash));
          subDir.mkdirs();
          final File file = new File(subDir, jarFileName);
          if (!file.isDirectory() && !file.getName().endsWith(".jar"))
            continue;

          final URL jarUrl = new URL(resource, jarEntry.substring(path.length()));
          Files.copy(jarUrl.openStream(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);

          if (!callback.test(file))
            file.delete();
        }
      }
      while (resources.hasMoreElements());

      if (outDir != null)
        deleteOnShutdown(outDir);
    }
    catch (final IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private static Set<File> deleteDirs;

  private static final Predicate<File> deletePredicate = new Predicate<File>() {
    @Override
    public boolean test(final File t) {
      return t.delete();
    }
  };

  private static class ShutdownHook extends Thread {
    @Override
    public void run() {
      for (final File deleteDir : deleteDirs) {
        AssembleUtil.recurseDir(deleteDir, deletePredicate);
      }
    }
  }

  private static void deleteOnShutdown(final File dir) {
    if (deleteDirs == null) {
      deleteDirs = new HashSet<>();
      Runtime.getRuntime().addShutdownHook(new ShutdownHook());
    }

    deleteDirs.add(dir);
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