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
import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarFile;

import com.sun.tools.attach.VirtualMachine;

import io.opentracing.Tracer;
import io.opentracing.mock.MockTracer;
import io.opentracing.util.GlobalTracer;

/**
 * The SpecialAgent.
 *
 * @author Seva Safris
 */
@SuppressWarnings("restriction")
public class SpecialAgent extends SpecialAgentBase {
  private static final Logger logger = Logger.getLogger(SpecialAgent.class);

  private static class ClassLoaderMap<T> extends IdentityHashMap<ClassLoader,T> {
    private static final long serialVersionUID = 5515722666603482519L;

    /**
     * This method is modified to support value lookups where the key is a
     * "proxy" class loader representing the bootstrap class loader. This
     * pattern is used by ByteBuddy, whereby the proxy class loader is an
     * {@code URLClassLoader} that has an empty classpath and a null parent.
     * <p>
     * {@inheritDoc}
     */
    @Override
    @SuppressWarnings("unlikely-arg-type")
    public T get(final Object key) {
      T value = super.get(key);
      if (value != null || !(key instanceof URLClassLoader))
        return value;

      final URLClassLoader classLoader = (URLClassLoader)key;
      return classLoader.getURLs().length > 0 || classLoader.getParent() != null ? null : super.get(null);
    }
  }

  private static final String DEFINE_CLASS = ClassLoader.class.getName() + ".defineClass";
  private static final Map<File,PluginManifest> fileToPluginManifest = new LinkedHashMap<>();
  private static final ClassLoaderMap<Map<Integer,Boolean>> classLoaderToCompatibility = new ClassLoaderMap<>();
  private static final ClassLoaderMap<List<RuleClassLoader>> classLoaderToRuleClassLoader = new ClassLoaderMap<>();
  private static final Map<File,File[]> pluginFileToDependencies = new HashMap<>();

  private static PluginsClassLoader pluginsClassLoader;

  // FIXME: ByteBuddy is now the only Instrumenter. Should this complexity be removed?
  private static final Instrumenter instrumenter = Instrumenter.BYTEBUDDY;

  private static Instrumentation inst;
  private static final long startTime;

  static {
    startTime = System.currentTimeMillis();
    SpecialAgentUtil.assertJavaAgentJarName();
  }

  public static void main(final String[] args) throws Exception {
    if (args.length != 1) {
      System.err.println("Usage: <PID>");
      System.exit(1);
    }

    final VirtualMachine vm = VirtualMachine.attach(args[0]);
    final String agentPath = SpecialAgent.class.getProtectionDomain().getCodeSource().getLocation().getPath();
    try {
      vm.loadAgent(agentPath, SpecialAgentUtil.getInputArguments());
    }
    finally {
      vm.detach();
    }
  }

  private static final String loggingConfigClassProperty = "java.util.logging.config.class";

  /**
   * Main entrypoint to load the {@code SpecialAgent} via static attach.
   *
   * @param agentArgs Agent arguments.
   * @param inst The {@code Instrumentation}.
   */
  public static void premain(final String agentArgs, final Instrumentation inst) {
    try {
      if (agentArgs != null)
        AssembleUtil.absorbProperties(agentArgs);

      loadProperties();

      final String loggingConfigClass = System.clearProperty(loggingConfigClassProperty);

      try {
        BootLoaderAgent.premain(inst);
        SpecialAgent.inst = inst;

        final String initDefer = System.getProperty("sa.init.defer");
        if (initDefer != null && !"false".equals(initDefer)) {
          SpringAgent.premain(inst, new Runnable() {
            @Override
            public void run() {
              try {
                instrumenter.manager.premain(null, inst);
              }
              catch (final Exception e) {
                throw new ExceptionInInitializerError(e);
              }
            }
          });
        }
        else {
          instrumenter.manager.premain(null, inst);
        }
      }
      finally {
        if (loggingConfigClass != null)
          System.setProperty(loggingConfigClassProperty, loggingConfigClass);
      }

      final long startupTime = (System.currentTimeMillis() - startTime) / 10;
      if (logger.isLoggable(Level.FINE))
        logger.fine("Started SpecialAgent in " + (startupTime / 100d) + "s\n");
    }
    catch (final Throwable t) {
      logger.log(Level.SEVERE, "Terminating SpecialAgent due to:", t);
    }

    AgentRule.initialized = true;
  }

  /**
   * Main entrypoint to load the {@code SpecialAgent} via dynamic attach.
   *
   * @param agentArgs Agent arguments.
   * @param inst The {@code Instrumentation}.
   * @throws Exception If an error has occurred.
   */
  public static void agentmain(final String agentArgs, final Instrumentation inst) throws Exception {
    premain(agentArgs, inst);
  }

  private static IsoClassLoader isoClassLoader;

  /**
   * Main initialization method for the {@code SpecialAgent}. This method is
   * called by the re/transformation {@link Manager} instance.
   *
   * @param manager The {@link Manager} instance to be used for initialization.
   */
  static void initialize(final Manager manager) {
    if (logger.isLoggable(Level.FINEST))
      logger.finest("Agent#initialize() java.class.path:\n  " + System.getProperty("java.class.path").replace(File.pathSeparator, "\n  "));

    final Map<String,String> properties = new HashMap<>();
    for (final Map.Entry<Object,Object> property : System.getProperties().entrySet()) {
      final String key = String.valueOf(property.getKey());
      final String value = properties.get(key);
      if (value != null && !value.equals(property.getValue()))
        throw new IllegalStateException("System property " + key + " is specified twice with different values");

      properties.put(key, property.getValue() == null ? null : String.valueOf(property.getValue()));
    }

    // Process the system properties to determine which Instrumentation and Tracer Plugins to enable
    final ArrayList<String> verbosePluginNames = new ArrayList<>();
    File[] includedPlugins = null;
    final HashMap<String,Boolean> instruPluginNameToEnable = new HashMap<>();
    final HashMap<String,Boolean> tracerPluginNameToEnable = new HashMap<>();
    for (final Map.Entry<String,String> property : properties.entrySet()) {
      final String key = property.getKey();
      final String value = property.getValue();
      if (key.startsWith("sa.instrumentation.plugin.")) {
        if (key.endsWith(".verbose"))
          verbosePluginNames.add(key.substring(26, key.length() - 8));
        else if (key.endsWith(".enable"))
          instruPluginNameToEnable.put(key.substring(26, key.length() - 7), Boolean.parseBoolean(value));
        else if (key.endsWith(".disable"))
          instruPluginNameToEnable.put(key.substring(26, key.length() - 8), "false".equals(value));
        else if (key.length() == 33 && key.endsWith(".include")) {
          final String[] includedPluginPaths = value.split(File.pathSeparator);
          includedPlugins = new File[includedPluginPaths.length];
          for (int i = 0; i < includedPluginPaths.length; ++i)
            includedPlugins[i] = new File(includedPluginPaths[i]);
        }
      }
      else if (key.startsWith("sa.tracer.plugin.")) {
        if (key.endsWith(".enable"))
          tracerPluginNameToEnable.put(key.substring(17, key.length() - 7), Boolean.parseBoolean(value));
        else if (key.endsWith(".disable"))
          tracerPluginNameToEnable.put(key.substring(17, key.length() - 8), "false".equals(value));
      }
    }

    final boolean allInstruEnabled = !instruPluginNameToEnable.containsKey("*") || instruPluginNameToEnable.remove("*");
    if (logger.isLoggable(Level.FINER))
      logger.finer("Instrumentation Plugins are " + (allInstruEnabled ? "en" : "dis") + "abled by default");

    final boolean allTracerEnabled = !tracerPluginNameToEnable.containsKey("*") || tracerPluginNameToEnable.remove("*");
    if (logger.isLoggable(Level.FINER))
      logger.finer("Tracer Plugins are " + (allTracerEnabled ? "en" : "dis") + "abled by default");

    final Supplier<File> destDir = new Supplier<File>() {
      private File destDir = null;

      @Override
      public File get() {
        try {
          return destDir == null ? destDir = Files.createTempDirectory("opentracing-specialagent").toFile() : destDir;
        }
        catch (final IOException e) {
          throw new IllegalStateException(e);
        }
      }
    };

    final List<URL> isoUrls = new ArrayList<>();

    // Process the ext JARs from AssembleUtil#META_INF_EXT_PATH
    SpecialAgentUtil.findJarResources(UtilConstants.META_INF_ISO_PATH, destDir, new Predicate<File>() {
      @Override
      public boolean test(final File file) {
        try {
          isoUrls.add(new URL("file", "", file.getAbsolutePath()));
          return true;
        }
        catch (final MalformedURLException e) {
          throw new IllegalStateException(e);
        }
      }
    });

    isoClassLoader = new IsoClassLoader(isoUrls.toArray(new URL[isoUrls.size()]));

    // Process the plugin JARs from AssembleUtil#META_INF_PLUGIN_PATH
    final Predicate<File> loadPluginPredicate = new Predicate<File>() {
      @Override
      public boolean test(final File t) {
        // Then, identify whether the JAR is an Instrumentation or Tracer Plugin
        final PluginManifest pluginManifest = PluginManifest.getPluginManifest(t);
        boolean enablePlugin = true;
        if (pluginManifest != null) {
          final boolean isInstruPlugin = pluginManifest.type == PluginManifest.Type.INSTRUMENTATION;
          // Next, see if it is included or excluded
          enablePlugin = isInstruPlugin ? allInstruEnabled : allTracerEnabled;
          final Map<String,Boolean> pluginNameToEnable = isInstruPlugin ? instruPluginNameToEnable : tracerPluginNameToEnable;
          for (final String pluginName : verbosePluginNames) {
            final String namePattern = SpecialAgentUtil.convertToNameRegex(pluginName);
            if (pluginManifest.name.equals(pluginName) || pluginManifest.name.matches(namePattern)) {
              System.setProperty("sa." + (isInstruPlugin ? "instrumentation" : "tracer") + ".plugin." + pluginManifest.name + ".verbose", "true");
              break;
            }
          }

          for (final Map.Entry<String,Boolean> entry : pluginNameToEnable.entrySet()) {
            final String namePattern = SpecialAgentUtil.convertToNameRegex(entry.getKey());
            if (pluginManifest.name.equals(entry.getKey()) || pluginManifest.name.matches(namePattern)) {
              enablePlugin = entry.getValue();
              if (logger.isLoggable(Level.FINER))
                logger.finer((isInstruPlugin ? "Instrumentation" : "Tracer") + " Plugin " + pluginManifest.name + " is " + (enablePlugin ? "en" : "dis") + "abled");

              break;
            }
          }
        }

        if (!enablePlugin)
          return false;

        fileToPluginManifest.put(t, pluginManifest);
        return true;
      }
    };

    // First load all plugins explicitly included with the `-Dsa.instrumentation.plugin.include=...` system property.
    if (includedPlugins != null)
      for (final File includedPlugin : includedPlugins)
        loadPluginPredicate.test(includedPlugin);

    // Then, load the plugins inside the SpecialAgent JAR.
    SpecialAgentUtil.findJarResources(UtilConstants.META_INF_PLUGIN_PATH, destDir, loadPluginPredicate);

    if (fileToPluginManifest.size() == 0 && logger.isLoggable(Level.FINER))
      logger.finer("Must be running from a test, because no JARs were found under " + UtilConstants.META_INF_PLUGIN_PATH);

    try {
      // Add instrumentation rule JARs from system class loader
      final Enumeration<URL> instrumentationRules = manager.getResources();
      while (instrumentationRules.hasMoreElements()) {
        final File pluginFile = SpecialAgentUtil.getSourceLocation(instrumentationRules.nextElement(), manager.file);
        fileToPluginManifest.put(pluginFile, PluginManifest.getPluginManifest(pluginFile));
      }
    }
    catch (final IOException e) {
      throw new IllegalStateException(e);
    }

    // Add plugins specified on in the RULE_PATH_ARG
    final File[] pluginFiles = SpecialAgentUtil.classPathToFiles(System.getProperty(RULE_PATH_ARG));
    if (pluginFiles != null)
      for (final File pluginFile : pluginFiles)
        if (!fileToPluginManifest.containsKey(pluginFile))
          fileToPluginManifest.put(pluginFile, PluginManifest.getPluginManifest(pluginFile));

    // Identify all non-null PluginManifest(s), put them into a list,
    // sort it based on priority, and re-add to fileToPluginManifest
    final List<PluginManifest> pluginManifests = new ArrayList<>();
    final Iterator<Map.Entry<File,PluginManifest>> iterator = fileToPluginManifest.entrySet().iterator();
    while (iterator.hasNext()) {
      final Map.Entry<File,PluginManifest> entry = iterator.next();
      if (entry.getValue() != null) {
        pluginManifests.add(entry.getValue());
        iterator.remove();
      }
    }

    Collections.sort(pluginManifests, new Comparator<PluginManifest>() {
      @Override
      public int compare(final PluginManifest o1, final PluginManifest o2) {
        return Integer.compare(o1.getPriority(), o2.getPriority());
      }
    });

    for (final PluginManifest pluginManifest : pluginManifests)
      fileToPluginManifest.put(pluginManifest.file, pluginManifest);

    if (logger.isLoggable(Level.FINER))
      logger.finer("Loading " + fileToPluginManifest.size() + " rule paths:\n" + AssembleUtil.toIndentedString(fileToPluginManifest.keySet()));

    pluginsClassLoader = new PluginsClassLoader(fileToPluginManifest.keySet());

    final Map<String,String> nameToVersion = new HashMap<>();
    final int count = loadDependencies(pluginsClassLoader, nameToVersion) + loadDependencies(ClassLoader.getSystemClassLoader(), nameToVersion);
    if (count == 0)
      logger.log(Level.SEVERE, "Could not find " + DEPENDENCIES_TGF + " in any rule JARs");

    deferredTracer = loadTracer();
    loadRules(manager);
  }

  /**
   * Returns the {@code JarFile} referencing the Tracer Plugin by the given
   * {@code name} in the specified {@code ClassLoader}.
   *
   * @param classLoader The {@code ClassLoader} in which to find the Tracer
   *          Plugin.
   * @param name The short name of the Tracer Plugin.
   * @return The {@code URL} referencing the Tracer Plugin by the given
   *         {@code name} in the specified {@code ClassLoader}, or {@code null}
   *         if one was not found.
   */
  private static URL findTracer(final ClassLoader classLoader, final String name) {
    try {
      final Enumeration<URL> enumeration = classLoader.getResources(TRACER_FACTORY);
      final Set<URL> urls = new HashSet<>();
      while (enumeration.hasMoreElements()) {
        final URL url = enumeration.nextElement();
        if (urls.contains(url))
          continue;

        urls.add(url);
        if (logger.isLoggable(Level.FINEST))
          logger.finest("Found " + TRACER_FACTORY + ": <" + AssembleUtil.getNameId(url) + ">" + url);

        final String jarPath = SpecialAgentUtil.getSourceLocation(url, TRACER_FACTORY).getPath();
        final String fileName = SpecialAgentUtil.getName(jarPath);
        final String tracerName = fileName.substring(0, fileName.lastIndexOf('.'));
        if (name.equals(tracerName))
          return new URL("file", null, jarPath);
      }

      return null;
    }
    catch (final IOException e) {
      throw new IllegalStateException(e);
    }
  }

  /**
   * Loads all dependencies.tgf files, and cross-links the dependency references
   * with the matching rule JARs.
   *
   * @param classLoader The {@code ClassLoader} in which to search for
   *          dependencies.tgf files.
   * @return The number of dependencies.tgf files that were loaded.
   */
  private static int loadDependencies(final ClassLoader classLoader, final Map<String,String> nameToVersion) {
    int count = 0;
    try {
      final Enumeration<URL> enumeration = classLoader.getResources(DEPENDENCIES_TGF);
      final Set<String> urls = new HashSet<>();
      while (enumeration.hasMoreElements()) {
        final URL url = enumeration.nextElement();
        if (urls.contains(url.toString()))
          continue;

        urls.add(url.toString());
        if (logger.isLoggable(Level.FINEST))
          logger.finest("Found " + DEPENDENCIES_TGF + ": <" + AssembleUtil.getNameId(url) + ">" + url);

        final File jarFile = SpecialAgentUtil.getSourceLocation(url, DEPENDENCIES_TGF);

        final String dependenciesTgf = new String(AssembleUtil.readBytes(url));
        final String firstLine = dependenciesTgf.substring(0, dependenciesTgf.indexOf('\n'));
        final String version = firstLine.substring(firstLine.lastIndexOf(':') + 1);

        final PluginManifest pluginManifest = fileToPluginManifest.get(jarFile);
        if (pluginManifest == null)
          throw new IllegalStateException("Expected to find PluginManifest for file: " + jarFile + " in: " + fileToPluginManifest.keySet());

        final String exists = nameToVersion.get(pluginManifest.name);
        if (exists != null && !exists.equals(version))
          throw new IllegalStateException("Illegal attempt to overwrite previously defined version for: " + pluginManifest.name);

        nameToVersion.put(pluginManifest.name, version);

        final File[] dependencyFiles = MavenUtil.filterRuleURLs(pluginsClassLoader.getFiles(), dependenciesTgf, false, "compile");
        if (logger.isLoggable(Level.FINEST))
          logger.finest("  URLs from " + DEPENDENCIES_TGF + ": " + AssembleUtil.toIndentedString(dependencyFiles));

        if (dependencyFiles == null)
          throw new UnsupportedOperationException("Unsupported " + DEPENDENCIES_TGF + " encountered: " + url + "\nPlease file an issue on https://github.com/opentracing-contrib/java-specialagent/");

        boolean foundReference = false;
        for (final File dependencyFile : dependencyFiles) {
          if (pluginsClassLoader.containsPath(dependencyFile)) {
            // When run from a test, it may happen that both the "allPluginsClassLoader"
            // and SystemClassLoader have the same path, leading to the same dependencies.tgf
            // file to be processed twice. This check asserts the previously registered
            // dependencies are correct.
            foundReference = true;
            final File[] registeredDependencyFiles = pluginFileToDependencies.get(dependencyFile);
            if (registeredDependencyFiles != null) {
              if (registeredDependencyFiles == pluginFileToDependencies.get(jarFile))
                continue;

              throw new IllegalStateException("Dependencies already registered for " + dependencyFile + ". Are there multiple rule JARs with " + DEPENDENCIES_TGF + " referencing the same rule JAR? Offending JAR: " + jarFile);
            }

            if (logger.isLoggable(Level.FINEST))
              logger.finest("Registering dependencies for " + jarFile + " and " + dependencyFile + ":\n" + AssembleUtil.toIndentedString(dependencyFiles));

            ++count;
            pluginFileToDependencies.put(jarFile, dependencyFiles);
            // Why did I link each `dependencyFile` to the `dependencyFiles`?
            // Removing this due to: [LS-10518]
            // pluginFileToDependencies.put(dependencyFile, dependencyFiles);
          }
        }

        if (!foundReference)
          throw new IllegalStateException("Could not find a rule JAR referenced in " + jarFile + DEPENDENCIES_TGF + " from: \n" + AssembleUtil.toIndentedString(dependencyFiles));
      }
    }
    catch (final IOException e) {
      throw new IllegalStateException(e);
    }

    return count;
  }

  /**
   * This method loads any OpenTracing {@code AgentRule}s, delegated to the
   * instrumentation {@link Manager} in the runtime.
   */
  private static void loadRules(final Manager manager) {
    try {
      if (logger.isLoggable(Level.FINE))
        logger.fine("\n<<<<<<<<<<<<<<<<<<<<< Loading AgentRule(s) >>>>>>>>>>>>>>>>>>>>>\n");

      if (pluginsClassLoader == null) {
        logger.severe("Attempt to load OpenTracing agent rules before allPluginsClassLoader initialized");
        return;
      }

      try {
        // Create map from rule jar URL to its index in allPluginsClassLoader.getURLs()
        final Map<File,Integer> ruleJarToIndex = new HashMap<>();
        for (int i = 0; i < pluginsClassLoader.getFiles().length; ++i)
          ruleJarToIndex.put(pluginsClassLoader.getFiles()[i], i);

        manager.loadRules(pluginsClassLoader, ruleJarToIndex, SpecialAgentUtil.digestEventsProperty(System.getProperty(LOG_EVENTS_PROPERTY)), fileToPluginManifest);
      }
      catch (final IOException e) {
        logger.log(Level.SEVERE, "Failed to load OpenTracing agent rules", e);
      }
    }
    finally {
    if (logger.isLoggable(Level.FINE))
      logger.fine("\n>>>>>>>>>>>>>>>>>>>>> Loaded AgentRule(s) <<<<<<<<<<<<<<<<<<<<<<\n");
    }
  }

  public static boolean isAgentRunner() {
    return System.getProperty(AGENT_RUNNER_ARG) != null;
  }

  private static Tracer deferredTracer;

  static Tracer getDeferredTracer() {
    return deferredTracer;
  }

  /**
   * Connects a Tracer Plugin to the runtime.
   *
   * @return A {@code Tracer} instance to be deferred, or null if no tracer was
   *         specified or the specified tracer was loaded.
   */
  @SuppressWarnings("resource")
  private static Tracer loadTracer() {
    if (logger.isLoggable(Level.FINE))
      logger.fine("\n<<<<<<<<<<<<<<<<<<<<<<<< Loading Tracer >>>>>>>>>>>>>>>>>>>>>>>>\n");

    try {
      if (GlobalTracer.isRegistered()) {
        if (logger.isLoggable(Level.FINE))
          logger.fine("Tracer already registered with GlobalTracer");

        return null;
      }

      final String tracerProperty = System.getProperty(TRACER_PROPERTY);
      if (tracerProperty == null) {
        if (logger.isLoggable(Level.FINE))
          logger.fine("Tracer was not specified with \"" + TRACER_PROPERTY + "\" system property");

        return null;
      }

      if (logger.isLoggable(Level.FINE))
        logger.fine("Resolving tracer:\n  " + tracerProperty);

      final Tracer tracer;
      if ("mock".equals(tracerProperty)) {
        tracer = new MockTracer();
      }
      else {
        final ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        final File file = new File(tracerProperty);
        try {
          final URL tracerUrl = file.exists() ? new URL("file", null, file.getPath()) : findTracer(pluginsClassLoader, tracerProperty);
          final URL tracerResolverResourceUrl = pluginsClassLoader.findResource("io/opentracing/contrib/tracerresolver/TracerResolver.class");
          if (tracerResolverResourceUrl == null)
            throw new IllegalStateException("Could not find TracerResolver");

          final String tracerResovlerResourcePath = tracerResolverResourceUrl.toString();
          final URL tracerResovlerUrl = new URL(tracerResovlerResourcePath.substring(4, tracerResovlerResourcePath.indexOf('!')));
          final ClassLoader parent = System.getProperty("java.version").startsWith("1.") ? null : (ClassLoader)ClassLoader.class.getMethod("getPlatformClassLoader").invoke(null);
          AgentRuleUtil.tracerClassLoader = new URLClassLoader(new URL[] {tracerResovlerUrl}, parent);
          Thread.currentThread().setContextClassLoader(AgentRuleUtil.tracerClassLoader);
          if (tracerUrl != null) {
            // If the desired tracer is in its own JAR file, or if this is not
            // running in an AgentRunner test (because in this case the tracer
            // is in a JAR also, which is inside the SpecialAgent JAR), then
            // isolate the tracer JAR in its own class loader.
            if (file.exists() || !isAgentRunner()) {
              AgentRuleUtil.tracerClassLoader = new TracerClassLoader(AgentRuleUtil.tracerClassLoader, tracerUrl, tracerResovlerUrl);
              Thread.currentThread().setContextClassLoader(AgentRuleUtil.tracerClassLoader);
            }
          }
          else if (findTracer(ClassLoader.getSystemClassLoader(), tracerProperty) == null) {
            throw new IllegalStateException(TRACER_PROPERTY + "=" + tracerProperty + " did not resolve to a tracer JAR or name");
          }

          final Class<?> tracerResolverClass = Class.forName("io.opentracing.contrib.tracerresolver.TracerResolver", true, AgentRuleUtil.tracerClassLoader);
          final Method resolveTracerMethod = tracerResolverClass.getMethod("resolveTracer");
          tracer = (Tracer)resolveTracerMethod.invoke(null);
          Thread.currentThread().setContextClassLoader(contextClassLoader);
        }
        catch (final ClassNotFoundException | IllegalAccessException | InvocationTargetException | IOException | NoSuchMethodException e) {
          throw new IllegalStateException(e);
        }
      }

      if (tracer == null) {
        logger.warning("Tracer was NOT RESOLVED");
        return null;
      }

      if (!isAgentRunner() && !GlobalTracer.registerIfAbsent(tracer))
        throw new IllegalStateException("There is already a registered global Tracer.");

      if (logger.isLoggable(Level.FINE))
        logger.fine("Tracer was resolved and " + (isAgentRunner() ? "deferred to be registered" : "registered") + " with GlobalTracer:\n  " + tracer.getClass().getName() + " from " + (tracer.getClass().getProtectionDomain().getCodeSource() == null ? "null" : tracer.getClass().getProtectionDomain().getCodeSource().getLocation().getPath()));

      return tracer;
    }
    finally {
      if (logger.isLoggable(Level.FINE))
        logger.fine("\n>>>>>>>>>>>>>>>>>>>>>>>> Loaded Tracer <<<<<<<<<<<<<<<<<<<<<<<<<\n");
    }
  }

  @SuppressWarnings("resource")
  public static boolean linkRule(final int index, final ClassLoader classLoader) {
    Map<Integer,Boolean> indexToCompatibility = classLoaderToCompatibility.get(classLoader);
    Boolean compatible;
    if (indexToCompatibility == null) {
      synchronized (classLoaderToCompatibility) {
        indexToCompatibility = classLoaderToCompatibility.get(classLoader);
        if (indexToCompatibility == null) {
          classLoaderToCompatibility.put(classLoader, indexToCompatibility = new ConcurrentHashMap<>());
        }
      }

      compatible = null;
    }
    else {
      compatible = indexToCompatibility.get(index);
    }

    if (compatible != null && compatible) {
      if (logger.isLoggable(Level.FINER)) {
        final File pluginFile = pluginsClassLoader.getFiles()[index];
        final PluginManifest pluginManifest = fileToPluginManifest.get(pluginFile);
        logger.finer("SpecialAgent#linkRule(\"" + pluginManifest.name + "\"[" + index + "], " + AssembleUtil.getNameId(classLoader) + "): compatible = " + compatible + " [cached]");
      }

      return true;
    }

    // Find the Plugin File (identified by index passed to this method)
    final File pluginFile = pluginsClassLoader.getFiles()[index];
    final PluginManifest pluginManifest = fileToPluginManifest.get(pluginFile);

    if (logger.isLoggable(Level.FINER))
      logger.finer("SpecialAgent#linkRule(\"" + pluginManifest.name + "\"[" + index + "], " + AssembleUtil.getNameId(classLoader) + "): compatible = " + compatible + ", RulePath: " + pluginFile);

    // Now find all the paths that pluginFile depends on, by reading dependencies.tgf
    final File[] pluginDependencyFiles = pluginFileToDependencies.get(pluginFile);
    if (pluginDependencyFiles == null)
      throw new IllegalStateException("No " + DEPENDENCIES_TGF + " was registered for: " + pluginFile);

    if (logger.isLoggable(Level.FINEST))
      logger.finest("[" + pluginManifest.name + "] new " + RuleClassLoader.class.getSimpleName() + "([\n" + AssembleUtil.toIndentedString(pluginDependencyFiles) + "]\n, " + AssembleUtil.getNameId(classLoader) + ");");

    // Create an isolated (no parent class loader) URLClassLoader with the pluginDependencyFiles
    final RuleClassLoader ruleClassLoader = new RuleClassLoader(pluginManifest, isoClassLoader, classLoader, pluginDependencyFiles);
    compatible = ruleClassLoader.isCompatible(classLoader);
    indexToCompatibility.put(index, compatible);
    if (!compatible) {
      try {
        ruleClassLoader.close();
      }
      catch (final IOException e) {
        logger.log(Level.WARNING, "[" + pluginManifest.name + "] Failed to close " + RuleClassLoader.class.getSimpleName() + ": " + AssembleUtil.getNameId(ruleClassLoader), e);
      }

      return false;
    }

    if (classLoader == null) {
      if (logger.isLoggable(Level.FINER))
        logger.finer("[" + pluginManifest.name + "] Target class loader is: <bootstrap>null");

      for (final File pluginDependencyFile : pluginDependencyFiles) {
        try {
          final File file = new File(pluginDependencyFile.getPath());
          inst.appendToBootstrapClassLoaderSearch(file.isFile() ? new JarFile(file) : SpecialAgentUtil.createTempJarFile(file));
        }
        catch (final IOException e) {
          logger.log(Level.SEVERE, "[" + pluginManifest.name + "] Failed to add path to bootstrap class loader: " + pluginDependencyFile.getPath(), e);
        }
      }
    }
    else if (classLoader == ClassLoader.getSystemClassLoader()) {
      if (logger.isLoggable(Level.FINER))
        logger.finer("[" + pluginManifest.name + "] Target class loader is: <system>" + AssembleUtil.getNameId(classLoader));

      for (final File pluginDependencyFile : pluginDependencyFiles) {
        try {
          inst.appendToSystemClassLoaderSearch(pluginDependencyFile.isFile() ? new JarFile(pluginDependencyFile) : SpecialAgentUtil.createTempJarFile(pluginDependencyFile));
        }
        catch (final IOException e) {
          logger.log(Level.SEVERE, "[" + pluginManifest.name + "] Failed to add path to system class loader: " + pluginDependencyFile, e);
        }
      }
    }
    else if (logger.isLoggable(Level.FINER)) {
      logger.finer("[" + pluginManifest.name + "] Target class loader is: " + AssembleUtil.getNameId(classLoader));
    }

    // Associate the RuleClassLoader with the target class's class loader
    List<RuleClassLoader> ruleClassLoaders = classLoaderToRuleClassLoader.get(classLoader);
    if (ruleClassLoaders == null) {
      synchronized (classLoaderToRuleClassLoader) {
        ruleClassLoaders = classLoaderToRuleClassLoader.get(classLoader);
        if (ruleClassLoaders == null) {
          classLoaderToRuleClassLoader.put(classLoader, ruleClassLoaders = new IdentityList<>(new ArrayList<RuleClassLoader>()));
        }
      }
    }

    synchronized (ruleClassLoaders) {
      if (!ruleClassLoaders.contains(ruleClassLoader)) {
        ruleClassLoaders.add(ruleClassLoader);
      }
    }

    // Attempt to preload classes if the callstack is not coming from
    // ClassLoader#defineClass
    for (final StackTraceElement stackTraceElement : Thread.currentThread().getStackTrace()) {
      if (DEFINE_CLASS.equals(stackTraceElement.getClassName() + "." + stackTraceElement.getMethodName())) {
        if (logger.isLoggable(Level.FINER))
          logger.finer("[" + pluginManifest.name + "] Preload of instrumentation classes deferred");

        return true;
      }
    }

    if (logger.isLoggable(Level.FINER))
      logger.finer("[" + pluginManifest.name + "] Preload of instrumentation classes called");

    ruleClassLoader.preLoad(classLoader);
    return true;
  }

  private static <R,T extends Throwable>R invoke(final String name, final ClassLoader classLoader, final QuadFunction<ClassLoader,String,List<RuleClassLoader>,ClassLoader,R,T> function) throws T {
    // Check if the class loader matches a ruleClassLoader
    List<RuleClassLoader> ruleClassLoaders;
    ClassLoader linkedLoader = classLoader;
    while (true) {
      ruleClassLoaders = classLoaderToRuleClassLoader.get(linkedLoader);
      if (ruleClassLoaders != null) {
        final R r = function.apply(classLoader, name, ruleClassLoaders, linkedLoader);
        if (r != null)
          return r;
      }

      if (linkedLoader == null)
        return null;

      linkedLoader = linkedLoader.getParent();
    }
  }

  private static final QuadFunction<ClassLoader,String,List<RuleClassLoader>,ClassLoader,Boolean,RuntimeException> preLoad = new QuadFunction<ClassLoader,String,List<RuleClassLoader>,ClassLoader,Boolean,RuntimeException>() {
    @Override
    public Boolean apply(final ClassLoader classLoader, final String name, final List<RuleClassLoader> ruleClassLoaders, final ClassLoader linkedLoader) {
      for (int i = 0; i < ruleClassLoaders.size(); ++i) {
        final RuleClassLoader ruleClassLoader = ruleClassLoaders.get(i);
        ruleClassLoader.preLoad(linkedLoader);
      }

      return Boolean.TRUE;
    }
  };

  public static void preLoad(final ClassLoader classLoader) {
    if (logger.isLoggable(Level.FINEST))
      logger.finest(">>>>>>>> preLoad(" + AssembleUtil.getNameId(classLoader) + ")");

    invoke(null, classLoader, preLoad);
  }

  private static final QuadFunction<ClassLoader,String,List<RuleClassLoader>,ClassLoader,byte[],RuntimeException> findClass = new QuadFunction<ClassLoader,String,List<RuleClassLoader>,ClassLoader,byte[],RuntimeException>() {
    @Override
    public byte[] apply(final ClassLoader classLoader, final String name, final List<RuleClassLoader> ruleClassLoaders, final ClassLoader linkedLoader) {
      final String resourceName = name.replace('.', '/').concat(".class");
      for (int i = 0; i < ruleClassLoaders.size(); ++i) {
        final RuleClassLoader ruleClassLoader = ruleClassLoaders.get(i);
        if (ruleClassLoader.isClosed(linkedLoader))
          continue;

        final URL resourceUrl = ruleClassLoader.getResource(resourceName);
        if (resourceUrl == null)
          continue;

        // Return the resource's bytes
        final byte[] bytecode = AssembleUtil.readBytes(resourceUrl);
        if (logger.isLoggable(Level.FINEST))
          logger.finest(">>>>>>>> findClass(" + AssembleUtil.getNameId(classLoader) + ", \"" + name + "\"): BYTECODE " + (bytecode != null ? "!" : "=") + "= null");

        return bytecode;
      }

      if (logger.isLoggable(Level.FINEST))
        logger.finest(">>>>>>>> findClass(" + AssembleUtil.getNameId(classLoader) + ", \"" + name + "\"): Not found in " + ruleClassLoaders.size() + " RuleClassLoader(s)");

      return null;
    }
  };

  /**
   * Returns the bytecode of the {@code Class} by the name of {@code name}, if
   * the {@code classLoader} matched a rule {@code ClassLoader} that contains
   * OpenTracing instrumentation classes intended to be loaded into
   * {@code classLoader}. This method is called by the {@link ClassLoaderAgentRule}.
   * This method returns {@code null} if it cannot locate the bytecode for the
   * requested {@code Class}, or if it has already been called for
   * {@code classLoader} and {@code name}.
   *
   * @param classLoader The {@code ClassLoader} to match to a
   *          {@link RuleClassLoader} that contains Instrumentation Plugin
   *          classes intended to be loaded into {@code classLoader}.
   * @param name The name of the {@code Class} to be found.
   * @return The bytecode of the {@code Class} by the name of {@code name}, or
   *         {@code null} if this method has already been called for
   *         {@code classLoader} and {@code name}.
   */
  public static byte[] findClass(final ClassLoader classLoader, final String name) {
    return invoke(name, classLoader, findClass);
  }

  private static final QuadFunction<ClassLoader,String,List<RuleClassLoader>,ClassLoader,URL,RuntimeException> findResource = new QuadFunction<ClassLoader,String,List<RuleClassLoader>,ClassLoader,URL,RuntimeException>() {
    @Override
    public URL apply(final ClassLoader classLoader, final String name, final List<RuleClassLoader> ruleClassLoaders, final ClassLoader linkedLoader) {
      for (int i = 0; i < ruleClassLoaders.size(); ++i) {
        final RuleClassLoader ruleClassLoader = ruleClassLoaders.get(i);
        if (ruleClassLoader.isClosed(linkedLoader))
          continue;

        final URL resource = ruleClassLoader.findResource(name);
        if (resource != null)
          return resource;
      }

      return null;
    }
  };

  public static URL findResource(final ClassLoader classLoader, final String name) {
    if (logger.isLoggable(Level.FINEST))
      logger.finest(">>>>>>>> findResource(" + AssembleUtil.getNameId(classLoader) + ", \"" + name + "\")");

    return invoke(name, classLoader, findResource);
  }

  private static final QuadFunction<ClassLoader,String,List<RuleClassLoader>,ClassLoader,Enumeration<URL>,IOException> findResources = new QuadFunction<ClassLoader,String,List<RuleClassLoader>,ClassLoader,Enumeration<URL>,IOException>() {
    @Override
    public Enumeration<URL> apply(final ClassLoader classLoader, final String name, final List<RuleClassLoader> ruleClassLoaders, final ClassLoader linkedLoader) throws IOException {
      for (int i = 0; i < ruleClassLoaders.size(); ++i) {
        final RuleClassLoader ruleClassLoader = ruleClassLoaders.get(i);
        if (ruleClassLoader.isClosed(linkedLoader))
          continue;

        final Enumeration<URL> resources = ruleClassLoader.findResources(name);
        if (resources.hasMoreElements())
          return resources;
      }

      return null;
    }
  };

  public static Enumeration<URL> findResources(final ClassLoader classLoader, final String name) throws IOException {
    if (logger.isLoggable(Level.FINEST))
      logger.finest(">>>>>>>> findResources(" + AssembleUtil.getNameId(classLoader) + ", \"" + name + "\")");

    return invoke(name, classLoader, findResources);
  }
}