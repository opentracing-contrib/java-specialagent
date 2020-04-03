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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class PluginManifest {
  private static final Logger logger = Logger.getLogger(PluginManifest.class);

  public static class Directory {
    private static final Comparator<PluginManifest> comparator = new Comparator<PluginManifest>() {
      @Override
      public int compare(final PluginManifest o1, final PluginManifest o2) {
        return Integer.compare(o1.getPriority(), o2.getPriority());
      }
    };

    private final LinkedHashMap<File,PluginManifest> fileToPluginManifest = new LinkedHashMap<>();

    public void put(File file, final PluginManifest pluginManifest) {
      if (pluginManifest != null)
        file = pluginManifest.file;

      fileToPluginManifest.put(file, pluginManifest);
      if (logger.isLoggable(Level.FINEST))
        logger.finest("PluginManifest.put(" + file + " <" + file.getAbsoluteFile() + ">, " + AssembleUtil.getSimpleNameId(pluginManifest) + ")");
    }

    public boolean containsKey(final File file) {
      final boolean result = fileToPluginManifest.containsKey(file.getAbsoluteFile());
      if (logger.isLoggable(Level.FINEST))
        logger.finest("PluginManifest.contains(" + file + " <" + file.getAbsoluteFile() + ">): " + result);

      return result;
    }

    public Set<File> keySet() {
      return fileToPluginManifest.keySet();
    }

    public PluginManifest get(final File file) {
      final PluginManifest result = fileToPluginManifest.get(file.getAbsoluteFile());
      if (logger.isLoggable(Level.FINEST))
        logger.finest("PluginManifest.get(" + file + " <" + file.getAbsoluteFile() + ">): " + AssembleUtil.getSimpleNameId(result));

      return result;
    }

    public int size() {
      return fileToPluginManifest.size();
    }

    /**
     * Filters all non-null {@link PluginManifest}s, and sorts them based on
     * priority.
     */
    public void sort() {
      final ArrayList<PluginManifest> pluginManifests = new ArrayList<>();
      final Iterator<Map.Entry<File,PluginManifest>> iterator = fileToPluginManifest.entrySet().iterator();
      while (iterator.hasNext()) {
        final Map.Entry<File,PluginManifest> entry = iterator.next();
        if (entry.getValue() != null) {
          pluginManifests.add(entry.getValue());
          iterator.remove();
        }
      }

      Collections.sort(pluginManifests, comparator);
      for (final PluginManifest pluginManifest : pluginManifests) {
        fileToPluginManifest.put(pluginManifest.file, pluginManifest);
      }
    }
  }

  enum Type {
    INSTRUMENTATION,
    TRACER
  }

  private static final String RULE_NAME_PROPERTY = "sa.rule.name.";

  private static <D,P>PluginManifest getPluginManifestFromEntry(final File file, final String entry, final D dir, final P path, final BiFunction<D,P,String> entryToContent) {
    if (entry.startsWith(RULE_NAME_PROPERTY))
      return new PluginManifest(file, Type.INSTRUMENTATION, entry.substring(RULE_NAME_PROPERTY.length()), entryToContent.apply(dir, path));

    if ("META-INF/services/io.opentracing.contrib.tracerresolver.TracerFactory".equals(entry))
      return new PluginManifest(file, Type.TRACER, file.getName().substring(0, file.getName().length() - 4), null);

    return null;
  }

  public static PluginManifest id(final File file) {
    return new PluginManifest(file, null, null, null);
  }

  private static BiFunction<Path,Path,String> pathToClassName = new BiFunction<Path,Path,String>() {
    @Override
    public String apply(final Path t, final Path u) {
      try {
        return new String(Files.readAllBytes(u));
      }
      catch (final IOException e) {
        throw new IllegalStateException(e);
      }
    }
  };

  private static BiFunction<JarFile,JarEntry,String> jarToClassName = new BiFunction<JarFile,JarEntry,String>() {
    @Override
    public String apply(final JarFile t, final JarEntry u) {
      try (final InputStream in = t.getInputStream(u)) {
        return new String(AssembleUtil.readBytes(in));
      }
      catch (final IOException e) {
        throw new IllegalStateException(e);
      }
    }
  };

  public static PluginManifest getPluginManifest(final File file) {
    if (file.isDirectory()) {
      final PluginManifest[] pluginManifest = new PluginManifest[1];
      final Path dir = file.toPath();
      AssembleUtil.recurseDir(file, new Function<File,FileVisitResult>() {
        @Override
        public FileVisitResult apply(final File t) {
          final Path path = t.toPath();
          final String entry = dir.relativize(path).toString();
          if (entry.contains("/") && !entry.startsWith("META-INF"))
            return FileVisitResult.SKIP_SIBLINGS;

          pluginManifest[0] = getPluginManifestFromEntry(file, entry, dir, path, pathToClassName);
          return pluginManifest[0] != null ? FileVisitResult.TERMINATE : FileVisitResult.CONTINUE;
        }
      });

      return pluginManifest[0];
    }

    try (final JarFile jarFile = new JarFile(file)) {
      final Enumeration<JarEntry> entries = jarFile.entries();
      while (entries.hasMoreElements()) {
        final JarEntry entry = entries.nextElement();
        final PluginManifest pluginManifest = getPluginManifestFromEntry(file, entry.getName(), jarFile, entry, jarToClassName);
        if (pluginManifest != null)
          return pluginManifest;
      }

      return null;
    }
    catch (final IOException e) {
      throw new IllegalStateException(e);
    }
  }

  public final File file;
  public final Type type;
  public final String name;
  public final String adapterClassName;
  private URL fingerprintUrl;
  private int priority = -1;

  private PluginManifest(final File file, final Type type, final String name, final String adapterClassName) {
    this.file = file.getAbsoluteFile();
    this.type = type;
    this.name = name;
    this.adapterClassName = adapterClassName;
  }

  public int getPriority() {
    if (priority != -1)
      return priority;

    try {
      String pom = null;
      if (file.isDirectory()) {
        pom = new String(Files.readAllBytes(new File(new File(file, "../.."), "pom.xml").toPath()));
      }
      else {
        try (final ZipFile zipFile = new ZipFile(file)) {
          final Enumeration<? extends ZipEntry> enumeration = zipFile.entries();
          while (enumeration.hasMoreElements()) {
            final ZipEntry entry = enumeration.nextElement();
            if (entry.getName().startsWith("META-INF/maven/") && entry.getName().endsWith("pom.xml")) {
              try (final InputStream in = zipFile.getInputStream(entry)) {
                pom = new String(AssembleUtil.readBytes(in));
                break;
              }
            }
          }
        }

        if (pom == null)
          throw new FileNotFoundException("Could not find META-INF/maven/.../pom.xml in " + file);
      }

      final int start = pom.indexOf("<sa.rule.priority>");
      if (start == -1)
        return priority = 0;

      final int end = pom.indexOf("</sa.rule.priority>", start + 18);
      priority = Integer.parseInt(pom.substring(start + 18, end));
      if (priority < 0)
        throw new IllegalArgumentException("sa.rule.priority must be between 0 and 2147483647");

      return priority;
    }
    catch (final IOException e) {
      throw new IllegalStateException(e);
    }
  }

  public URL getFingerprint() {
    try {
      return fingerprintUrl == null ? fingerprintUrl = new URL(file.isDirectory() ? "file:" + file + "/fingerprint.bin" : "jar:file:" + file + "!/fingerprint.bin") : fingerprintUrl;
    }
    catch (final MalformedURLException e) {
      throw new IllegalStateException(e);
    }
  }

  private final HashMap<ClassLoader,Boolean> classLoaderToCompatibility = new HashMap<>();

  public Map<ClassLoader,Boolean> getClassLoaderToCompatibility() {
    return classLoaderToCompatibility;
  }

  @Override
  public int hashCode() {
    return file.hashCode();
  }

  @Override
  public boolean equals(final Object obj) {
    if (obj == this)
      return true;

    if (!(obj instanceof PluginManifest))
      return false;

    final PluginManifest that = (PluginManifest)obj;
    return file.equals(that.file);
  }
}