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
package io.opentracing.contrib.uberjar;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.jboss.byteman.agent.Retransformer;

/**
 * This class provides the ByteMan manager implementation for OpenTracing.
 *
 */
public class OpenTracingManager {
  private static final Logger logger = Logger.getLogger(OpenTracingManager.class.getName());
  private static final String AGENT_RULES = "otarules.btm";

  protected static Retransformer transformer;
  private static URLClassLoader allPluginsClassLoader;
  private static URL[] apiJars;

  /**
   * This method initializes the manager.
   *
   * @param trans The ByteMan retransformer.
   */
  public static void initialize(final Retransformer trans) {
    transformer = trans;

    try {
      final List<URL> resources = findJarPath("META-INF/opentracing/");
      System.out.println("Found " + resources.size() + " plugins");
      allPluginsClassLoader = new URLClassLoader(resources.toArray(new URL[resources.size()]), null);
      apiJars = new URL[] {
        allPluginsClassLoader.loadClass("io.opentracing.Span").getProtectionDomain().getCodeSource().getLocation(),
        allPluginsClassLoader.loadClass("io.opentracing.util.GlobalTracer").getProtectionDomain().getCodeSource().getLocation(),
        allPluginsClassLoader.loadClass("io.opentracing.noop.NoopTracerFactory").getProtectionDomain().getCodeSource().getLocation()
      };

      loadRules();
    }
    catch (final ClassNotFoundException | IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private static List<URL> findJarPath(final String path) throws IOException {
    final URL url = ClassLoader.getSystemClassLoader().getResource(path);
    if (url == null)
      return null;

    final JarURLConnection jarURLConnection = (JarURLConnection)url.openConnection();
    jarURLConnection.setUseCaches(false);
    final JarFile jarFile = jarURLConnection.getJarFile();

    final Path destDir = Files.createTempDirectory("opentracing");
    destDir.toFile().deleteOnExit();

    final List<URL> resources = new ArrayList<>();
    final Enumeration<JarEntry> enumeration = jarFile.entries();
    while (enumeration.hasMoreElements()) {
      final String entry = enumeration.nextElement().getName();
      if (entry.length() > path.length() && entry.startsWith(path)) {
        final int slash = entry.lastIndexOf('/');
        final File dir = new File(destDir.toFile(), entry.substring(0, slash));
        dir.mkdirs();
        dir.deleteOnExit();
        final File file = new File(dir, entry.substring(slash + 1));
        file.deleteOnExit();
        final URL u = new URL(url, entry.substring(path.length()));
        Files.copy(u.openStream(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        resources.add(file.toURI().toURL());
      }
    }

    return resources;
  }

  /**
   * This method loads any OpenTracing Agent rules (otarules.btm) found as
   * resources within the supplied classloader.
   */
  public static void loadRules() {
    if (allPluginsClassLoader == null) {
      logger.severe("Attempt to load OpenTracing agent rules before allPluginsClassLoader initialized");
      return;
    }

    if (transformer == null) {
      logger.severe("Attempt to load OpenTracing agent rules before transformer initialized");
      return;
    }

    final List<String> scripts = new ArrayList<>();
    final List<String> scriptNames = new ArrayList<>();

    try {
      // Prepare the ClassLoader rule
      loadRules(ClassLoader.getSystemClassLoader().getResource(AGENT_RULES), null, scriptNames, scripts);

      // Create map from plugin jar URL to its index in allPluginsClassLoader.getURLs()
      final Map<String,Integer> pluginJarToIndex = new HashMap<>();
      for (int i = 0; i < allPluginsClassLoader.getURLs().length; ++i)
        pluginJarToIndex.put(allPluginsClassLoader.getURLs()[i].toString(), i);

      // Prepare the Plugin rules
      final Enumeration<URL> enumeration = allPluginsClassLoader.getResources(AGENT_RULES);
      while (enumeration.hasMoreElements()) {
        final URL scriptUrl = enumeration.nextElement();
        final String pluginJar = scriptUrl.toString().substring(4, scriptUrl.toString().indexOf('!'));
        final int index = pluginJarToIndex.get(pluginJar);
        loadRules(scriptUrl, index, scriptNames, scripts);
      }

      final StringWriter sw = new StringWriter();
      try (final PrintWriter pw = new PrintWriter(sw)) {
        transformer.installScript(scripts, scriptNames, pw);
      }
      catch (final Exception e) {
        System.err.println("Failed to install scripts");
        e.printStackTrace();
      }

      System.out.println(sw.toString());
    }
    catch (final IOException e) {
      System.err.println("Failed to load OpenTracing agent rules");
      e.printStackTrace();
    }

    System.out.println("OpenTracing Agent rules loaded");
  }

  private static void loadRules(final URL url, final Integer index, final List<String> scriptNames, final List<String> scripts) throws IOException {
    System.out.println("Load rules for index " + index + " from URL = " + url);
    final StringBuilder builder = new StringBuilder();
    try (final InputStream in = url.openStream()) {
      final byte[] bytes = new byte[1024];
      for (int len; (len = in.read(bytes)) != -1;)
        builder.append(new String(bytes, 0, len));
    }

    final String script = builder.toString();
    if (index != null) {
      scripts.add(createClassLoadScript(script, index));
      scriptNames.add(url.toString() + "-discovery");
    }

    scripts.add(script);
    scriptNames.add(url.toString());
  }

  private static String createClassLoadScript(final String script, final int index) {
    final StringBuilder builder = new StringBuilder();
    final StringTokenizer tokenizer = new StringTokenizer(script, "\n\r");
    boolean method = false;
    while (tokenizer.hasMoreTokens()) {
      final String line = tokenizer.nextToken().trim();
      final String lineUC = line.toUpperCase();
      if (method) {
        builder.append("IF TRUE\n");
        builder.append("DO ").append(OpenTracingManager.class.getName()).append(".execClassLoadScript($this, ").append(index).append(")\n");
        builder.append("ENDRULE\n");
        return builder.toString();
      }
      else if (lineUC.startsWith("RULE ")) {
        builder.append(line).append(" (Discovery)\n");
      }
      else if (lineUC.startsWith("CLASS ") || lineUC.startsWith("INTERFACE ")) {
        builder.append(line).append('\n');
      }
      else if (lineUC.startsWith("METHOD ")) {
        method = true;
        builder.append(line).append('\n');
      }
    }

    throw new UnsupportedOperationException("Did not see line starting with: \"METHOD ...\"");
  }

  public static void execClassLoadScript(final Object caller, final int index) throws ClassNotFoundException, IOException {
    // Get the ClassLoader of the caller class
    final ClassLoader classLoader = caller.getClass().getClassLoader();

    // Collect the API JARs + the Plugin JAR (identified by index passed to this method)
    final URL[] pluginJarUrls = new URL[apiJars.length + 1];
    System.arraycopy(apiJars, 0, pluginJarUrls, 0, apiJars.length);
    pluginJarUrls[apiJars.length] = allPluginsClassLoader.getURLs()[index];

    // Create an isolated (no parent ClassLoader) URLClassLoader with the pluginJarUrls
    final URLClassLoader pluginClassLoader = new URLClassLoader(pluginJarUrls, null);

    // Associate the pluginClassLoader with the caller's classLoader
    OpenTracingInjector.classLoaderToPluginClassLoader.put(classLoader, pluginClassLoader);

    // Call Class.forName(...) for each class in pluginClassLoader to load in the caller's classLoader
    for (final URL jarUrl : pluginClassLoader.getURLs()) {
      try (final ZipInputStream zip = new ZipInputStream(jarUrl.openStream())) {
        for (ZipEntry entry; (entry = zip.getNextEntry()) != null;) {
          if (entry.getName().endsWith(".class")) {
            Class.forName(entry.getName().substring(0, entry.getName().length() - 6).replace('/', '.'), false, classLoader);
          }
        }
      }
    }
  }
}