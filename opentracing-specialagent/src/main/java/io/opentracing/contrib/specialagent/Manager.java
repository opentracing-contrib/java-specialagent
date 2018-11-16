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
 * Provides the Byteman manager implementation for OpenTracing.
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
      digestRule(ClassLoader.getSystemClassLoader().getResource("classloader.btm"), null, scripts, scriptNames);

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
        digestRule(scriptUrl, index, scripts, scriptNames);
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
          if (len != 0)
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
   * {@link #retrofitScript(String,int)} to create a "load classes" script that
   * is triggered in the same manner as the script at {@code url}, and is used
   * to load API and instrumentation classes into the calling object's
   * {@code ClassLoader}.
   *
   * @param url The {@code URL} to the script resource.
   * @param index The index of the OpenTracing instrumentation JAR in
   *          {@code allPluginsClassLoader.getURLs()} which corresponds to
   *          {@code script}.
   * @param scripts The list into which the script will be added.
   * @param scriptNames The list into which the script name will be added.
   */
  private static void digestRule(final URL url, final Integer index, final List<String> scripts, final List<String> scriptNames) {
    if (logger.isLoggable(Level.FINE))
      logger.fine("Load rules for index " + index + " from URL = " + url);

    final String script = readScript(url);
    scripts.add(index == null ? script : retrofitScript(script, index));
    scriptNames.add(url.toString());
  }

  /**
   * This method consumes a Byteman script that is intended for the
   * instrumentation of the OpenTracing API into a 3rd-party library, and
   * produces a Byteman script that is used to trigger the "load classes"
   * procedure {@link #linkPlugin(Object,int)} that loads the
   * instrumentation and OpenTracing API classes directly into the
   * {@code ClassLoader} in which the 3rd-party library is loaded.
   *
   * @param script The OpenTracing instrumentation script.
   * @param index The index of the OpenTracing instrumentation JAR in
   *          {@code allPluginsClassLoader.getURLs()} which corresponds to
   *          {@code script}.
   * @return The script used to trigger the "load classes" procedure
   *         {@link #linkPlugin(Object,int)}.
   */
  private static String retrofitScript(final String script, final int index) {
    final StringBuilder builder = new StringBuilder();
    final StringTokenizer tokenizer = new StringTokenizer(script, "\n\r");
    String classRef = null;
    String bindSpec = null;
    boolean hasBind = false;
    boolean inBind = false;
    while (tokenizer.hasMoreTokens()) {
      final String rawLine = tokenizer.nextToken();
      final String line = rawLine.trim();
      final String lineUC = line.toUpperCase();
      if (lineUC.startsWith("BIND")) {
        inBind = true;
        hasBind = true;
        if (builder.length() > 0)
          builder.append('\n');

        final String inLineBind = line.substring(4).trim();
        builder.append(bindSpec);
        if (inLineBind.length() > 0)
          builder.append("  ").append(inLineBind).append('\n');
      }
      else if (lineUC.startsWith("IF ")) {
        inBind = false;
        if (!hasBind)
          builder.append(bindSpec);

        builder.append("IF ");
        final String condition = line.substring(3).trim();
        if ("TRUE".equalsIgnoreCase(condition))
          builder.append("cOmPaTiBlE\n");
        else
          builder.append("cOmPaTiBlE AND ").append(condition).append('\n');
      }
      else if (lineUC.startsWith("AT ")) {
        inBind = false;
        builder.append(rawLine).append('\n');
      }
      else if (inBind) {
        builder.append(rawLine.replace("=", "= !cOmPaTiBlE ? null :")).append('\n');
      }
      else {
        builder.append(rawLine).append('\n');
        inBind = false;
        if (lineUC.startsWith("CLASS ")) {
          classRef = line.substring(6).trim() + ".class";
        }
        else if (lineUC.startsWith("INTERFACE ")) {
          classRef = null;
        }
        else if (lineUC.startsWith("METHOD ")) {
          bindSpec = "BIND\n  cOmPaTiBlE = " + Manager.class.getName() + ".linkPlugin(" + index + ", " + classRef + ", $*);\n";
        }
      }
    }

    return builder.toString();
  }

  /**
   * An {@link URLClassLoader} that encloses an instrumentation plugin, and
   * provides the following functionalities:
   * <ol>
   * <li>{@link #isCompatible(ClassLoader)}: Determines whether the
   * instrumentation plugin it repserents is compatible with a specified
   * {@code ClassLoader}.</li>
   * <li>{@link #markFindResource(ClassLoader,String)}: Keeps track of the
   * names of classes that have been loaded into a specified
   * {@code ClassLoader}.</li>
   * </ol>
   */
  static class PluginClassLoader extends URLClassLoader {
    private final Map<ClassLoader,Boolean> compatibility = new IdentityHashMap<>();
    private final Map<ClassLoader,Set<String>> classLoaderToClassName = new IdentityHashMap<>();

    /**
     * Creates a new {@code PluginClassLoader} with the specified classpath URLs
     * and parent {@code ClassLoader}.
     *
     * @param urls The classpath URLs.
     * @param parent The parent {@code ClassLoader}.
     */
    PluginClassLoader(final URL[] urls, final ClassLoader parent) {
      super(urls, parent);
    }

    /**
     * Returns {@code true} if the instrumentation plugin represented by this
     * instance is compatible with its target classes that are loaded in the
     * specified {@code ClassLoader}.
     * <p>
     * This method utilizes the {@link LibraryFingerprint} class to determine
     * compatibility via "fingerprinting".
     * <p>
     * Once "fingerprinting" has been performed, the resulting value is
     * associated with the specified {@code ClassLoader} in the
     * {@link #compatibility} map as a cache.
     *
     * @param classLoader The {@code ClassLoader} for which the instrumentation
     *          plugin represented by this {@code PluginClassLoader} is to be
     *          checked for compatibility.
     * @return {@code true} if the target classes in the specified
     *         {@code ClassLoader} are compatible with the instrumentation
     *         plugin represented by this {@code PluginClassLoader}, and
     *         {@code false} if the specified {@code ClassLoader} is
     *         incompatible.
     */
    boolean isCompatible(final ClassLoader classLoader) {
      final Boolean compatible = compatibility.get(classLoader);
      if (compatible != null)
        return compatible;

      final URL fpURL = getResource("fingerprint.bin");
      if (fpURL != null) {
        try {
          final LibraryFingerprint fingerprint = LibraryFingerprint.fromFile(fpURL);
          final FingerprintError[] errors = fingerprint.matchesRuntime(classLoader, 0, 0);
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
      }
      else {
        // TODO: Parameterize the default behavior!
        logger.warning("Resorting to default behavior (permit instrumentation) due to \"fingerprint.bin not found\" in: " + Util.toIndentedString(getURLs()));
      }

      compatibility.put(classLoader, true);
      return true;
    }

    /**
     * Marks the specified resource name with {@code true}, associated with the
     * specified {@code ClassLoader}, and returns the previous value of the
     * mark.
     * <p>
     * The first invocation of this method for a specified {@code ClassLoader}
     * and resource name will return {@code false}.
     * <p>
     * Subsequent calls to this method for a specified {@code ClassLoader} and
     * resource name will return {@code true}.
     *
     * @param classLoader The {@code ClassLoader} to which the value of the mark
     *          for the specified resource name is associated.
     * @param resourceName The name of the resource as the target of the mark.
     * @return {@code false} if this method was never called with the specific
     *         {@code ClassLoader} and resource name; {@code true} if this
     *         method was previously called with the specific
     *         {@code ClassLoader} and resource name.
     */
    boolean markFindResource(final ClassLoader classLoader, final String resourceName) {
      Set<String> classNames = classLoaderToClassName.get(classLoader);
      if (classNames == null)
        classLoaderToClassName.put(classLoader, classNames = new HashSet<>());
      else if (classNames.contains(resourceName))
        return true;

      classNames.add(resourceName);
      return false;
    }
  }

  private static void unloadRule(final String ruleName) {
    if (logger.isLoggable(Level.FINE))
      logger.fine("Uninstalling rule: " + ruleName);

    final StringWriter sw = new StringWriter();
    try (final PrintWriter pw = new PrintWriter(sw)) {
      transformer.removeScripts(Collections.singletonList("RULE " + ruleName), pw);
    }
    catch (final Exception e) {
      logger.log(Level.SEVERE, "Failed to uninstall rule: " + ruleName, e);
    }

    if (logger.isLoggable(Level.FINE))
      logger.fine(sw.toString());
  }

  /**
   * Links the instrumentation plugin at the specified index. This method is
   * called by Byteman upon trigger of a rule from a otarules.btm script, and
   * its purpose is:
   * <ol>
   * <li>To link a plugin JAR to the {@code ClassLoader} in which the
   * instrumentation plugin is relevant (i.e. a {@code ClassLoader} which
   * contains the target classes of instrumentation).</li>
   * <li>To check if the instrumentation code is compatible with the classes
   * that are to be instrumented in the {@code ClassLoader}.</li>
   * <li>To return the value of the compatibility test, in order to allow the
   * rule to skip its logic in case the test does not pass.</li>
   * </ol>
   * The {@code index} is a reference to the array index of the plugin JAR's
   * {@code URL} in {@link #allPluginsClassLoader}, which is statically declared
   * during the script retrofit in {@link #retrofitScript(String,int)}.
   * <p>
   * The {@code args} parameter is used to obtain the caller object, which is
   * itself used to determine the {@code ClassLoader} in which the classes
   * relevant for instrumentation are being invoked, and are thus loaded. If the
   * caller object is null (meaning the triggered method is static), the
   * {@code cls} parameter is used to determine the target {@code ClassLoader}.
   * This method thereafter associates (in
   * {@link #classLoaderToPluginClassLoader}) a {@link PluginClassLoader} for
   * the instrumentation plugin at {@code index}. The association thereafter
   * allows the {@link #findClass(ClassLoader,String)} method to directly inject
   * the bytecode of the instrumentation classes into the target
   * {@code ClassLoader}.
   *
   * @param index The index of the plugin JAR's {@code URL} in
   *          {@link #allPluginsClassLoader}.
   * @param cls The class on which the trigger event occurred.
   * @param args The arguments used for the triggered method call.
   * @see #retrofitScript(String,int)
   */
  @SuppressWarnings("resource")
  public static boolean linkPlugin(final int index, final Class<?> cls, final Object[] args) {
    if (logger.isLoggable(Level.FINEST))
      logger.finest("linkPlugin(" + index + ", " + (cls == null ? "null" : cls.getName()) + ".class, " + Arrays.toString(args) + ")");

    Rule.disableTriggers();
    try {
      // Get the ClassLoader of the target class
      final Class<?> targetClass = args[0] != null ? args[0].getClass() : cls;
      final ClassLoader classLoader = targetClass.getClassLoader();

      // Find the Plugin JAR (identified by index passed to this method)
      final URL pluginJar = allPluginsClassLoader.getURLs()[index];
      if (logger.isLoggable(Level.FINEST))
        logger.finest("  Plugin JAR: " + pluginJar);

      // Create an isolated (no parent ClassLoader) URLClassLoader with the pluginJarUrls
      final PluginClassLoader pluginClassLoader = new PluginClassLoader(new URL[] {pluginJar}, classLoader);
      if (pluginClassLoader.isCompatible(classLoader)) {
        // Associate the pluginClassLoader with the target class's classLoader
        classLoaderToPluginClassLoader.put(classLoader, pluginClassLoader);
        return true;
      }

      return false;
    }
    finally {
      Rule.enableTriggers();
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
    Rule.disableTriggers();
    try {
      // Check if the classLoader matches a pluginClassLoader
      final PluginClassLoader pluginClassLoader = classLoaderToPluginClassLoader.get(classLoader);
      if (pluginClassLoader == null)
        return null;

      // Check that the resourceName has not already been retrieved by this method
      // (this may be a moot check, because the JVM won't call findClass() twice
      // for the same class)
      final String resourceName = name.replace('.', '/').concat(".class");
      if (pluginClassLoader.markFindResource(classLoader, resourceName))
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
    finally {
      Rule.enableTriggers();
    }
  }
}