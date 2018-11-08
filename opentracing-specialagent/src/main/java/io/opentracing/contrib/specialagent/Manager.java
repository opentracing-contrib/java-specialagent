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

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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

import org.jboss.byteman.agent.Retransformer;
import org.jboss.byteman.rule.Rule;

/**
 * Provides the ByteMan manager implementation for OpenTracing.
 */
public class Manager {
  private static final Logger logger = Logger.getLogger(Manager.class.getName());
  private static final String AGENT_RULES = "otarules.btm";

  private static final Map<ClassLoader,PluginClassLoader> classLoaderToPluginClassLoader = new IdentityHashMap<>();

  protected static Retransformer transformer;
  private static URLClassLoader allPluginsClassLoader;

  /**
   * This method initializes the manager.
   *
   * @param trans The ByteMan retransformer.
   */
  public static void initialize(final Retransformer trans) {
    transformer = trans;

    final URL[] classpath = Util.classPathToURLs(System.getProperty("java.class.path"));
    final List<URL> pluginJarUrls = Util.findResources("META-INF/opentracing-specialagent/");
    if (logger.isLoggable(Level.FINE))
      logger.fine("Loading " + (pluginJarUrls == null ? null : pluginJarUrls.size()) + " plugin JARs");

    if (logger.isLoggable(Level.FINEST))
      logger.finest("Process classpath: " + Util.toIndentedString(classpath));

    // Override parent ClassLoader methods to avoid delegation of resource
    // resolution to BootLoader
    allPluginsClassLoader = new URLClassLoader(classpath, new ClassLoader(null) {
      // This is overridden to ensure resources are not discovered in BootClassLoader
      @Override
      public Enumeration<URL> getResources(final String name) throws IOException {
        return null;
      }
    });

    loadRules();
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
      loadRules(ClassLoader.getSystemClassLoader().getResource("classloader.btm"), null, scripts, scriptNames);

      // Create map from plugin jar URL to its index in
      // allPluginsClassLoader.getURLs()
      final Map<String,Integer> pluginJarToIndex = new HashMap<>();
      for (int i = 0; i < allPluginsClassLoader.getURLs().length; ++i)
        pluginJarToIndex.put(allPluginsClassLoader.getURLs()[i].toString(), i);

      // Prepare the Plugin rules
      final Enumeration<URL> enumeration = allPluginsClassLoader.getResources(AGENT_RULES);
      while (enumeration.hasMoreElements()) {
        final URL scriptUrl = enumeration.nextElement();
        final int bang = scriptUrl.toString().indexOf('!');
        final String pluginJar;
        if (bang != -1)
          pluginJar = scriptUrl.toString().substring(4, bang);
        else
          pluginJar = scriptUrl.toString().substring(0, scriptUrl.toString().lastIndexOf('/') + 1);

        if (logger.isLoggable(Level.FINEST))
          logger.finest("Dereferencing index for " + pluginJar);

        final int index = pluginJarToIndex.get(pluginJar);
        loadRules(scriptUrl, index, scripts, scriptNames);
      }

      installScripts(scripts, scriptNames);
    }
    catch (final IOException e) {
      logger.log(Level.SEVERE, "Failed to load OpenTracing agent rules", e);
    }

    if (logger.isLoggable(Level.FINE))
      logger.fine("OpenTracing Agent rules loaded");
  }

  private static void installScripts(final List<String> scripts, final List<String> scriptNames) {
    if (logger.isLoggable(Level.FINE))
      logger.fine("Installing rules: " + Util.toIndentedString(scriptNames));

    if (logger.isLoggable(Level.FINEST))
      for (final String script : scripts)
        logger.finest(script);

    final StringWriter sw = new StringWriter();
    try (final PrintWriter pw = new PrintWriter(sw)) {
      transformer.installScript(scripts, scriptNames, pw);
    }
    catch (final Exception e) {
      e.printStackTrace();
      logger.log(Level.SEVERE, "Failed to install scripts", e);
    }

    if (logger.isLoggable(Level.FINE))
      logger.fine(sw.toString());
  }

  private static String readScript(final URL url) {
    try {
      final StringBuilder builder = new StringBuilder();
      try (final InputStream in = url.openStream()) {
        final byte[] bytes = new byte[1024];
        for (int len; (len = in.read(bytes)) != -1;)
          builder.append(new String(bytes, 0, len));
      }

      return builder.toString();
    }
    catch (final IOException e) {
      throw new IllegalStateException(e);
    }
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
  private static void loadRules(final URL url, final Integer index, final List<String> scripts, final List<String> scriptNames) {
    if (logger.isLoggable(Level.FINE))
      logger.fine("Load rules for index " + index + " from URL = " + url);

    final String script = readScript(url);
    if (index != null) {
      final String discovery = createLoadClasses(script, index);
      scripts.add(discovery);
      final String scriptName = url.toString();
      scriptNames.add(scriptName.substring(0, scriptName.length() - 4) + "-discovery.btm");
    }
    else {
      scripts.add(script);
      scriptNames.add(url.toString());
    }
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
    String classRef = null;
    String methodName = null;
    int argc = 0;
    while (tokenizer.hasMoreTokens()) {
      final String line = tokenizer.nextToken().trim();
      final String lineUC = line.toUpperCase();
      if (methodName != null) {
        builder.append("BIND\n");
        builder.append("  compatible = ").append(Manager.class.getName()).append(".triggerLoadClasses(").append(index).append(", " + classRef + ", \"" + methodName + "\", " + argc + ", $*);\n");
        builder.append("IF compatible\n");
        builder.append("DO\n");
        builder.append("  traceln(\">>>>>>>> RE-INVOKING...\");\n");
        builder.append("  RETURN $0.").append(methodName).append('(');
        for (int i = 1; i <= argc; ++i) {
          if (i > 1)
            builder.append(',');

          builder.append('$').append(i);
        }

        builder.append(");\n");
        builder.append("ENDRULE\n");
        return builder.toString();
      }

      if (lineUC.startsWith("RULE ")) {
        builder.append(line).append(" (Discovery)\n");
      }
      else if (lineUC.startsWith("CLASS ")) {
        builder.append(line).append('\n');
        classRef = line.substring(6).trim() + ".class";
      }
      else if (lineUC.startsWith("INTERFACE ")) {
        builder.append(line).append('\n');
      }
      else if (lineUC.startsWith("METHOD ")) {
        builder.append(line).append('\n');
        builder.append("HELPER io.opentracing.contrib.specialagent.Helper\n");
        methodName = line.substring(7).trim();
        final int o = methodName.indexOf('(');
        if (o != -1) {
          final int c = methodName.indexOf(')', o);
          if (c - o > 1)
            argc = Util.getOccurrences(methodName.substring(o + 1, c), ',') + 1;

          methodName = methodName.substring(0, o);
        }
      }
    }

    throw new UnsupportedOperationException("Did not see line starting with: \"METHOD ...\"");
  }

  static class PluginClassLoader extends URLClassLoader {
    private final Map<ClassLoader,Boolean> compatibility = new IdentityHashMap<>();
    private final Map<ClassLoader,Set<String>> classLoaderToClassName = new IdentityHashMap<>();

    PluginClassLoader(final URL[] urls, final ClassLoader parent) {
      super(urls, parent);
    }

    boolean isCompatible(final ClassLoader classLoader) {
      final Boolean compatible = compatibility.get(classLoader);
      if (compatible != null)
        return compatible;

      final URL fpURL = getResource("fingerprint.bin");
      if (fpURL != null) {
        Rule.disableTriggers();
        try {
          final LibraryFingerprint digest = LibraryFingerprint.fromFile(fpURL);
          final FingerprintError[] errors = digest.matchesRuntime(classLoader, 0, 0);
          if (errors != null) {
            logger.warning("Disallowing instrumentation due to \"fingerprint.bin mismatch\" errors:\n" + Util.toIndentedString(errors) + " in: " + Util.toIndentedString(getURLs()));
            compatibility.put(classLoader, false);
            return false;
          }

          if (logger.isLoggable(Level.FINE))
            logger.fine("Allowing instrumentation due to \"fingerprint.bin match\" for: " + Util.toIndentedString(getURLs()));
        }
        catch (final IOException e) {
          // TODO: Parameterize the default behavior!
          logger.log(Level.SEVERE, "Resorting to default behavior (permit instrumentation) due to \"fingerprint.bin read error\" in: " + Util.toIndentedString(getURLs()), e);
        }
        finally {
          Rule.enableTriggers();
        }
      }
      else {
        // TODO: Parameterize the default behavior!
        logger.warning("Resorting to default behavior (permit instrumentation) due to \"fingerprint.bin not found\" in: " + Util.toIndentedString(getURLs()));
      }

      compatibility.put(classLoader, true);
      final URL url = getResource(AGENT_RULES);
      installScripts(Collections.singletonList(readScript(url)), Collections.singletonList(url.toString()));
      return true;
    }

    boolean shouldFindResource(final ClassLoader classLoader, final String resourceName) {
      Set<String> classNames = classLoaderToClassName.get(classLoader);
      if (classNames == null)
        classLoaderToClassName.put(classLoader, classNames = new HashSet<>());
      else if (classNames.contains(resourceName))
        return false;

      classNames.add(resourceName);
      return true;
    }
  }

  private static final ThreadLocal<Boolean> lock = new ThreadLocal<Boolean>() {
    @Override
    protected Boolean initialValue() {
      return false;
    }
  };

  /**
   * FIXME: Rewrite this... Execute the "load classes" procedure. This method is
   * called by Byteman when a "*-discovery" script is triggered. The
   * {@code caller} object is used to determine the target {@code ClassLoader}
   * into which the plugin at {@code index} must be loaded. This method calls
   * {@code Class.forName(...)} on each class in the plugin jar, in order to
   * trigger the "OpenTracing ClassLoader Injection" Byteman script.
   *
   * @param index The index of the plugin jar URL in
   *          {@link #allPluginsClassLoader}
   * @param cls The class on which the trigger event occurred.
   * @param methodName The name of the method on which the trigger event
   *          occurred.
   * @param argc The number of expected args as declared in the Byteman rule.
   * @param args The arguments used for the triggered method call.
   */
  public static boolean triggerLoadClasses(final int index, final Class<?> cls, final String methodName, final int argc, final Object[] args) {
    if (lock.get()) {
      lock.set(false);
      return false;
    }

    if (argc != args.length - 1)
      throw new UnsupportedOperationException("The number of method parameters (" + argc + ") declared in rule does not match actual number of paramterers passed to: " + cls + "#" + methodName + " PLEASE UPDATE YOUR " + AGENT_RULES);

    final Class<?> targetClass = args[0] != null ? args[0].getClass() : cls;
    if (logger.isLoggable(Level.FINEST))
      logger.finest("triggerLoadClasses(" + index + ", " + cls.getName() + ".class, " + methodName + ", " + argc + ", " + Arrays.toString(args) + ")");

    // Get the ClassLoader of the target class
    final ClassLoader classLoader = targetClass.getClassLoader();

    // Find the Plugin JAR (identified by index passed to this method)
    final URL pluginJar = allPluginsClassLoader.getURLs()[index];
    if (logger.isLoggable(Level.FINEST))
      logger.finest("  Plugin JAR: " + pluginJar);

    // Create an isolated (no parent ClassLoader) URLClassLoader with the
    // pluginJarUrls
    final PluginClassLoader pluginClassLoader = new PluginClassLoader(new URL[] {pluginJar}, classLoader);

    // Associate the pluginClassLoader with the target class's classLoader
    classLoaderToPluginClassLoader.put(classLoader, pluginClassLoader);
    if (!pluginClassLoader.isCompatible(classLoader))
      return false;

    lock.set(true);
    return true;
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
    final PluginClassLoader pluginClassLoader = classLoaderToPluginClassLoader.get(classLoader);
    if (pluginClassLoader == null)
      return null;

    // Check that the resourceName has not already been retrieved by this method
    // (this may be a moot check, because the JVM won't call findClass() twice
    // for the same class)
    final String resourceName = name.replace('.', '/').concat(".class");
    if (!pluginClassLoader.shouldFindResource(classLoader, resourceName))
      return null;

    if (logger.isLoggable(Level.FINEST))
      logger.finest(">>>>>>>> findClass(" + Util.getIdentityCode(classLoader) + ", \"" + name + "\")");

    // Return the resource's bytes, or null if the resource does not exist in
    // pluginClassLoader
    try (final InputStream in = pluginClassLoader.getResourceAsStream(resourceName)) {
      return in == null ? null : Util.readBytes(in);
    }
    catch (final IOException e) {
      logger.log(Level.SEVERE, "Failed to read bytes for " + resourceName, e);
      return null;
    }
  }
}