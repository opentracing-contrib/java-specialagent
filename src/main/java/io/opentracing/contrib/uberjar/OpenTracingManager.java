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

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.jboss.byteman.agent.Retransformer;

/**
 * This class provides the ByteMan manager implementation for OpenTracing.
 */
public class OpenTracingManager {
  private static final Logger logger = Logger.getLogger(OpenTracingManager.class.getName());
  private static final String AGENT_RULES = "otarules.btm";

  private static final Map<ClassLoader,Set<String>> classLoaderToClassName = new IdentityHashMap<>();
  private static final Map<ClassLoader,URLClassLoader> classLoaderToPluginClassLoader = new IdentityHashMap<>();

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
      final List<URL> pluginJarUrls = OpenTracingUtil.findResources("META-INF/opentracing/");
      if (logger.isLoggable(Level.FINEST))
        logger.finest("Loading " + pluginJarUrls.size() + " plugin JARs");

      // Override parent ClassLoader methods to avoid delegation of resource
      // resolution to BootLoader
      allPluginsClassLoader = new URLClassLoader(pluginJarUrls.toArray(new URL[pluginJarUrls.size()]), new ClassLoader() {
        @Override
        public Enumeration<URL> getResources(final String name) throws IOException {
          return null;
        }
      });

      apiJars = new URL[] {
        // Add org.opentracing:opentracing-api
        allPluginsClassLoader.loadClass("io.opentracing.Span").getProtectionDomain().getCodeSource().getLocation(),
        // Add org.opentracing.contrib:opentracing-util
        allPluginsClassLoader.loadClass("io.opentracing.util.GlobalTracer").getProtectionDomain().getCodeSource().getLocation(),
        // Add org.opentracing.contrib:opentracing-noop
        allPluginsClassLoader.loadClass("io.opentracing.noop.NoopTracerFactory").getProtectionDomain().getCodeSource().getLocation()
      };

      loadRules();
    }
    catch (final ClassNotFoundException | IOException e) {
      throw new IllegalStateException(e);
    }
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
      loadRules(ClassLoader.getSystemClassLoader().getResource(AGENT_RULES), null, scripts, scriptNames);

      // Create map from plugin jar URL to its index in
      // allPluginsClassLoader.getURLs()
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
        logger.log(Level.SEVERE, "Failed to install scripts", e);
      }

      if (logger.isLoggable(Level.FINEST))
        logger.finest(sw.toString());
    }
    catch (final IOException e) {
      logger.log(Level.SEVERE, "Failed to load OpenTracing agent rules", e);
    }

    if (logger.isLoggable(Level.FINE))
      logger.fine("OpenTracing Agent rules loaded");
  }

  /**
   * This method digests the Byteman rule script at {@code url}, and adds the
   * script to the {@code scripts} and {@code scriptNames} lists. If
   * {@code index} is not null, this method calls
   * {@link #createLoadClasses(String,int)} to create a "load classes" script
   * that is triggered in the same manner as the script at {@code url}, and is
   * used to load API and instrumentation classes into the calling object's
   * {@code ClassLoader}.
   *
   * @param url The {@code URL} to the script resource.
   * @param index The index of the OpenTracing instrumentation JAR in
   *          {@code allPluginsClassLoader.getURLs()} which corresponds to
   *          {@code script}.
   * @param scripts The list of scripts.
   * @param scriptNames The list of script names.
   * @throws IOException If an I/O error has occurred.
   */
  private static void loadRules(final URL url, final Integer index, final List<String> scripts, final List<String> scriptNames) throws IOException {
    if (logger.isLoggable(Level.FINE))
      logger.fine("Load rules for index " + index + " from URL = " + url);

    final StringBuilder builder = new StringBuilder();
    try (final InputStream in = url.openStream()) {
      final byte[] bytes = new byte[1024];
      for (int len; (len = in.read(bytes)) != -1;)
        builder.append(new String(bytes, 0, len));
    }

    final String script = builder.toString();
    if (index != null) {
      scripts.add(createLoadClasses(script, index));
      scriptNames.add(url.toString() + "-discovery");
    }

    scripts.add(script);
    scriptNames.add(url.toString());
  }

  /**
   * This method consumes a Byteman script that is intended for the
   * instrumentation of the OpenTracing API into a 3rd-party library, and
   * produces a Byteman script that is used to trigger the "load classes"
   * procedure {@link #triggerLoadClasses(Object, int)} that loads the
   * instrumentation and OpenTracing API classes directly into the
   * {@code ClassLoader} in which the 3rd-party library is loaded.
   *
   * @param script The OpenTracing instrumentation script.
   * @param index The index of the OpenTracing instrumentation JAR in
   *          {@code allPluginsClassLoader.getURLs()} which corresponds to
   *          {@code script}.
   * @return The script used to trigger the "load classes" procedure
   *         {@link #triggerLoadClasses(Object, int)}.
   */
  private static String createLoadClasses(final String script, final int index) {
    final StringBuilder builder = new StringBuilder();
    final StringTokenizer tokenizer = new StringTokenizer(script, "\n\r");
    boolean methodSeen = false;
    while (tokenizer.hasMoreTokens()) {
      final String line = tokenizer.nextToken().trim();
      final String lineUC = line.toUpperCase();
      if (methodSeen) {
        builder.append("IF TRUE\n");
        builder.append("DO ").append(OpenTracingManager.class.getName()).append(".triggerLoadClasses($this, ").append(index).append(")\n");
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
        methodSeen = true;
        builder.append(line).append('\n');
      }
    }

    throw new UnsupportedOperationException("Did not see line starting with: \"METHOD ...\"");
  }

  /**
   * Execute the "load classes" procedure. This method is called by Byteman when
   * a "*-discovery" script is triggered. The {@code caller} object is used to
   * determine the target {@code ClassLoader} into which the plugin at
   * {@code index} must be loaded. This method calls {@code Class.forName(...)}
   * on each class in the plugin jar, in order to trigger the "OpenTracing
   * ClassLoader Injection" Byteman script.
   *
   * @param caller The caller object passed as $this by Byteman.
   * @param index The index of the plugin jar URL in
   *          allPluginsClassLoader.getURLs()
   */
  public static void triggerLoadClasses(final Object caller, final int index) {
    // Get the ClassLoader of the caller class
    final ClassLoader classLoader = caller.getClass().getClassLoader();

    // Collect the API JARs + the Plugin JAR (identified by index passed to this
    // method)
    final URL[] pluginJarUrls = new URL[apiJars.length + 1];
    System.arraycopy(apiJars, 0, pluginJarUrls, 0, apiJars.length);
    pluginJarUrls[apiJars.length] = allPluginsClassLoader.getURLs()[index];

    // Create an isolated (no parent ClassLoader) URLClassLoader with the
    // pluginJarUrls
    final URLClassLoader pluginClassLoader = new URLClassLoader(pluginJarUrls, classLoader);

    // Associate the pluginClassLoader with the caller's classLoader
    classLoaderToPluginClassLoader.put(classLoader, pluginClassLoader);

    // Call Class.forName(...) for each class in pluginClassLoader to load in
    // the caller's classLoader
    for (final URL jarUrl : pluginClassLoader.getURLs()) {
      try (final ZipInputStream zip = new ZipInputStream(jarUrl.openStream())) {
        for (ZipEntry entry; (entry = zip.getNextEntry()) != null;) {
          if (entry.getName().endsWith(".class")) {
            try {
              Class.forName(entry.getName().substring(0, entry.getName().length() - 6).replace('/', '.'), false, classLoader);
            }
            catch (final ClassNotFoundException e) {
              logger.log(Level.SEVERE, "Failed to load class", e);
            }
          }
        }
      }
      catch (final IOException e) {
        logger.log(Level.SEVERE, "Failed to read from JAR: " + jarUrl, e);
      }
    }
  }

  /**
   * Returns the bytecode of the {@code Class} by the name of {@code name}, if
   * the {@code classLoader} matched a plugin {@code ClassLoader} that contains
   * OpenTracing instrumentation classes intended to be loaded into
   * {@code classLoader}. This method is called by the "OpenTracing ClassLoader
   * Injection" Byteman script that is triggered by
   * {@link ClassLoader#findClass(String)}. This method returns {@code null} if
   * it cannot locate the bytecode for the requested {@code Class}, or if it has
   * already been called for {@code classLoader} and {@code name}.
   *
   * @param classLoader The {@code ClassLoader} to match to a plugin
   *          {@code ClassLoader} that contains OpenTracing instrumentation
   *          classes intended to be loaded into {@code classLoader}.
   * @param name The name of the {@code Class} to be found.
   * @return The bytecode of the {@code Class} by the name of {@code name}, or
   *         {@code null} if this method has already been called for
   *         {@code classLoader} and {@code name}.
   */
  public static byte[] findClass(final ClassLoader classLoader, final String name) {
    // Check if the classLoader matches a pluginClassLoader
    final URLClassLoader pluginClassLoader = classLoaderToPluginClassLoader.get(classLoader);
    if (pluginClassLoader == null)
      return null;

    // Check that the resourceName has not already been retrieved by this method
    // (this may be a moot point, because the JVM won't call findClass() twice
    // for the same class)
    final String resourceName = name.replace('.', '/').concat(".class");
    Set<String> classNames = classLoaderToClassName.get(classLoader);
    if (classNames == null)
      classLoaderToClassName.put(classLoader, classNames = new HashSet<>());
    else if (classNames.contains(resourceName))
      return null;

    classNames.add(resourceName);
    if (logger.isLoggable(Level.FINEST))
      logger.finest(">>>>>>>> findClass(" + OpenTracingUtil.getIdentityCode(classLoader) + ", \"" + name + "\")");

    // Return the resource's bytes, or null if the resource does not exist in
    // pluginClassLoader
    try (final InputStream in = pluginClassLoader.getResourceAsStream(resourceName)) {
      return in == null ? null : OpenTracingUtil.readBytes(in);
    }
    catch (final IOException e) {
      logger.log(Level.SEVERE, "Failed to read bytes for " + resourceName, e);
      return null;
    }
  }
}