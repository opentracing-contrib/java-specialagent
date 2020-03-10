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
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
  private enum AttachMode {
    STATIC,
    DYNAMIC,
    STATIC_DEFERRED
  }

  private static final Logger logger = Logger.getLogger(SpecialAgent.class);
  private static final String DEFINE_CLASS = ClassLoader.class.getName() + ".defineClass";
  private static final PluginManifest.Directory pluginManifestDirectory = new PluginManifest.Directory();
  private static final ClassLoaderMap<Map<Integer,Boolean>> classLoaderToCompatibility = new ClassLoaderMap<>();
  private static final ClassLoaderMap<List<RuleClassLoader>> classLoaderToRuleClassLoader = new ClassLoaderMap<>();
  private static final HashMap<File,File[]> pluginFileToDependencies = new HashMap<>();

  private static PluginsClassLoader pluginsClassLoader;
  private static IsoClassLoader isoClassLoader;

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
    final StringBuilder inputArguments = SpecialAgentUtil.getInputArguments();
    final int logFileProperty = inputArguments.indexOf("-Dsa.log.file=");
    if (logFileProperty > 0) {
      final int start = logFileProperty + 14;
      final int end = Math.max(inputArguments.indexOf(" ", start), inputArguments.length());
      final String filePath = inputArguments.substring(start, end);
      inputArguments.replace(start, end, new File(filePath).getAbsolutePath());
    }

    if (inputArguments.length() > 0)
      inputArguments.append(' ');

    inputArguments.append("-D").append(INIT_DEFER).append("=dynamic");
    try {
      vm.loadAgent(agentPath, inputArguments.toString());
    }
    finally {
      vm.detach();
    }
  }

  /**
   * Main entrypoint to load the {@code SpecialAgent} via dynamic attach.
   *
   * @param agentArgs Agent arguments.
   * @param inst The {@code Instrumentation} instance.
   * @throws Exception If an error has occurred.
   */
  public static void agentmain(final String agentArgs, final Instrumentation inst) throws Exception {
    premain(agentArgs, inst);
  }

  /**
   * Main entrypoint to load the {@code SpecialAgent} via static attach.
   *
   * @param agentArgs Agent arguments.
   * @param inst The {@code Instrumentation}.
   */
  public static void premain(final String agentArgs, final Instrumentation inst) {
    SpecialAgent.inst = inst;
    try {
      if (agentArgs != null)
        AssembleUtil.absorbProperties(agentArgs);

      init();
    }
    catch (final Throwable t) {
      logger.log(Level.SEVERE, "Terminating initialization of SpecialAgent due to:", t);
    }
  }

  /** Initialize the SpecialAgent in a sequence of steps. */
  private static void init() throws IOException, ReflectiveOperationException {
    if (logger.isLoggable(Level.FINE))
      logger.fine("Initializing SpecialAgent\n");

    // First, load system properties, in order to digest and absorb the
    // configuration properties into system properties.
    loadProperties();

    // Second, load the `BootLoaderAgent`, in order to allow the bootstrap
    // class loader to gain visibility of resources that are dynamically
    // appended in the runtime. This is necessary for ByteBuddy to be able to
    // dereference bytecode of classes appended via `Instrumentation`. By default,
    // such classes become part of the bootstrap class loader, but access to
    // the resources for the bytecode of these classes is not provided.
    BootLoaderAgent.premain(inst);

    // Third, load the `AgentRule` class, in order to load
    // `AgentRule.isThreadInstrumentable`, so that the lineage of threads can
    // be captured as early in the VM's lifecycle as possible.
    AgentRule.$Access.load();

    // Finally, load the Instrumentation Plugins and Tracer Plugins with the
    // provided `Manager`.
    load(instrumenter.manager);

    final long startupTime = (System.currentTimeMillis() - startTime) / 10;
    if (logger.isLoggable(Level.FINE))
      logger.fine("Initialized SpecialAgent in " + (startupTime / 100d) + "s\n");
  }

  /**
   * Main load method for the {@code SpecialAgent}, which is responsible for
   * loading Instrumentation Plugins and Tracer Plugins.
   *
   * @param manager The {@link Manager} instance.
   * @throws IOException If an I/O error has occurred.
   * @throws ReflectiveOperationException If a reflective operation error has
   *           occurred.
   */
  private static void load(final Manager manager) throws IOException, ReflectiveOperationException {
    if (logger.isLoggable(Level.FINEST))
      logger.finest("SpecialAgent#load(" + manager.getClass().getSimpleName() + ") java.class.path:\n  " + System.getProperty("java.class.path").replace(File.pathSeparator, "\n  "));

    final HashMap<String,String> properties = new HashMap<>();
    for (final Map.Entry<Object,Object> property : System.getProperties().entrySet()) {
      final String key = String.valueOf(property.getKey());
      final String value = properties.get(key);
      if (value != null && !value.equals(property.getValue()))
        throw new IllegalStateException("System property " + key + " is specified twice with different values: \"" + value + "\" and \"" + property.getValue() + "\"");

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
        if (key.indexOf(".verbose", 27) != -1)
          verbosePluginNames.add(key.substring(26, key.length() - 8));
        else if (key.indexOf(".enable", 27) != -1)
          instruPluginNameToEnable.put(key.substring(26, key.length() - 7), !"false".equals(value));
        else if (key.indexOf(".disable", 27) != -1)
          instruPluginNameToEnable.put(key.substring(26, key.length() - 8), "false".equals(value));
        else if (key.length() == 33 && key.endsWith(".include")) {
          final String[] includedPluginPaths = value.split(File.pathSeparator);
          includedPlugins = new File[includedPluginPaths.length];
          for (int i = 0; i < includedPluginPaths.length; ++i)
            includedPlugins[i] = new File(includedPluginPaths[i]);
        }
      }
      else if (key.startsWith("sa.tracer.plugin.")) {
        if (key.indexOf(".enable", 18) != -1)
          tracerPluginNameToEnable.put(key.substring(17, key.length() - 7), !"false".equals(value));
        else if (key.indexOf(".disable", 18) != -1)
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
      private File destDir;

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

    final ArrayList<URL> isoUrls = new ArrayList<>();

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
      public boolean test(final File file) {
        // Then, identify whether the JAR is an Instrumentation or Tracer Plugin
        final PluginManifest pluginManifest = PluginManifest.getPluginManifest(file);
        boolean enablePlugin = true;
        if (pluginManifest != null) {
          final boolean isInstruPlugin = pluginManifest.type == PluginManifest.Type.INSTRUMENTATION;
          // Next, see if it is included or excluded
          enablePlugin = isInstruPlugin ? allInstruEnabled : allTracerEnabled;
          final HashMap<String,Boolean> pluginNameToEnable = isInstruPlugin ? instruPluginNameToEnable : tracerPluginNameToEnable;
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

        pluginManifestDirectory.put(file, pluginManifest);
        return true;
      }
    };

    // First, load all plugins explicitly included with the `-Dsa.instrumentation.plugin.include=...` system property.
    if (includedPlugins != null)
      for (final File includedPlugin : includedPlugins)
        loadPluginPredicate.test(includedPlugin);

    // Then, load the plugins inside the SpecialAgent JAR.
    SpecialAgentUtil.findJarResources(UtilConstants.META_INF_PLUGIN_PATH, destDir, loadPluginPredicate);

    if (pluginManifestDirectory.size() == 0 && logger.isLoggable(Level.FINER))
      logger.finer("Must be running from a test, because no JARs were found under " + UtilConstants.META_INF_PLUGIN_PATH);

    // Add instrumentation rule JARs from system class loader
    final Enumeration<URL> instrumentationRules = manager.getResources();
    while (instrumentationRules.hasMoreElements()) {
      final File pluginFile = SpecialAgentUtil.getSourceLocation(instrumentationRules.nextElement(), manager.file);
      final PluginManifest pluginManifest = PluginManifest.getPluginManifest(pluginFile);
      pluginManifestDirectory.put(pluginManifest.file, pluginManifest);
    }

    // Add plugins specified on in the RULE_PATH_ARG
    final File[] pluginFiles = SpecialAgentUtil.classPathToFiles(System.getProperty(RULE_PATH_ARG));
    if (pluginFiles != null)
      for (final File pluginFile : pluginFiles)
        if (!pluginManifestDirectory.containsKey(pluginFile))
          pluginManifestDirectory.put(pluginFile, PluginManifest.getPluginManifest(pluginFile));

    // Sort the directory based on load priority
    pluginManifestDirectory.sort();
    if (logger.isLoggable(Level.FINER))
      logger.finer("Loading " + pluginManifestDirectory.size() + " rule paths:\n" + AssembleUtil.toIndentedString(pluginManifestDirectory.keySet()));

    pluginsClassLoader = new PluginsClassLoader(pluginManifestDirectory.keySet());

    final HashMap<String,String> nameToVersion = new HashMap<>();
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
  private static URL findTracer(final ClassLoader classLoader, final String name) throws IOException {
    final Enumeration<URL> enumeration = classLoader.getResources(TRACER_FACTORY);
    final HashSet<URL> urls = new HashSet<>();
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

  /**
   * Loads all dependencies.tgf files, and cross-links the dependency references
   * with the matching rule JARs.
   *
   * @param classLoader The {@code ClassLoader} in which to search for
   *          dependencies.tgf files.
   * @return The number of dependencies.tgf files that were loaded.
   */
  private static int loadDependencies(final ClassLoader classLoader, final Map<String,String> nameToVersion) throws IOException {
    int count = 0;
    final Enumeration<URL> enumeration = classLoader.getResources(DEPENDENCIES_TGF);
    final HashSet<String> urls = new HashSet<>();
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

      final PluginManifest pluginManifest = pluginManifestDirectory.get(jarFile);
      if (pluginManifest == null)
        throw new IllegalStateException("Expected to find " + PluginManifest.class.getSimpleName() + " for file: " + jarFile + " in: " + pluginManifestDirectory.keySet());

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

    return count;
  }

  /**
   * This method loads any OpenTracing {@code AgentRule}s, delegated to the
   * instrumentation {@link Manager} in the runtime.
   */
  private static void loadRules(final Manager manager) {
    AttachMode attachMode = AttachMode.STATIC_DEFERRED;
    try {
      if (logger.isLoggable(Level.FINE))
        logger.fine("\n<<<<<<<<<<<<<<<<<<<<< Loading AgentRule(s) >>>>>>>>>>>>>>>>>>>>\n");

      if (pluginsClassLoader == null)
        throw new IllegalStateException("Attempt to load OpenTracing agent rules before allPluginsClassLoader initialized");

      try {
        // Create map from rule jar URL to its index in allPluginsClassLoader.getURLs()
        final HashMap<File,Integer> ruleJarToIndex = new HashMap<>();
        for (int i = 0; i < pluginsClassLoader.getFiles().length; ++i)
          ruleJarToIndex.put(pluginsClassLoader.getFiles()[i], i);

        final LinkedHashMap<AgentRule,Integer> agentRules = new LinkedHashMap<>();
        final Manager.Event[] events = SpecialAgentUtil.digestEventsProperty(System.getProperty(LOG_EVENTS_PROPERTY));
        final Map<String,String> classNameToName = new HashMap<>();
        AgentRule.$Access.configure(new Runnable() {
          @Override
          public void run() {
            manager.loadRules(inst, agentRules, events);
          }
        }, classNameToName);

        final LinkedHashMap<AgentRule,Integer> deferrers = manager.scanRules(inst, agentRules, pluginsClassLoader, ruleJarToIndex, classNameToName, pluginManifestDirectory);

        final String initDeferProperty = System.getProperty(INIT_DEFER);
        if (initDeferProperty != null) {
          if ("dynamic".equals(initDeferProperty))
            attachMode = AttachMode.DYNAMIC;
          else if ("false".equals(initDeferProperty))
            attachMode = AttachMode.STATIC;
          else
            attachMode = AttachMode.STATIC_DEFERRED;
        }

        final String displayString = attachMode == AttachMode.STATIC_DEFERRED ? "Enabled: true (default)   " : "    Enabled: false        ";
        logger.info(".==============================================================.");
        logger.info("|                    Static Deferred Attach                    |");
        logger.info("|                    " + displayString + "                |");
        if (attachMode == AttachMode.DYNAMIC)
          logger.info("|                    (using dynamic attach)                    |");

        logger.info("|==============================================================|");
        if (attachMode == AttachMode.STATIC_DEFERRED) {
          logger.info("|               To disable Static Deferred Attach,             |");
          logger.info("|                 specify -Dsa.init.defer=false                |");
          logger.info("|=============================================================='");
        }

        if (deferrers == null) {
          if (attachMode == AttachMode.STATIC_DEFERRED) {
            logger.info("' 0 deferrers were detected, overriding to: -Dsa.init.defer=false");
            attachMode = AttachMode.STATIC;
            System.setProperty(INIT_DEFER, "false");
          }
          else {
            logger.info("' 0 deferrers were detected");
          }
        }
        else {
          logger.info("' " + deferrers.size() + " deferrers were detected:");
          for (final AgentRule deferrer : deferrers.keySet())
            logger.info("  " + deferrer.getClass().getName());

          if (attachMode == AttachMode.STATIC_DEFERRED) {
            // Just load the deferrers
            manager.loadRules(inst, deferrers, events);
            return;
          }
        }

        AgentRule.$Access.init();
      }
      catch (final IOException e) {
        logger.log(Level.SEVERE, "Failed to load OpenTracing agent rules", e);
      }

      return;
    }
    finally {
      if (logger.isLoggable(Level.FINE)) {
        if (attachMode == AttachMode.STATIC_DEFERRED)
          logger.fine("\n>>>>>>>>>>>>> Loading of AgentRule(s) is Deferred <<<<<<<<<<<<<\n");
        else
          logger.fine("\n>>>>>>>>>>>>>>>>>>>>> Loaded AgentRule(s) <<<<<<<<<<<<<<<<<<<<<\n");
      }
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
   * @throws IOException If an I/O error has occurred.
   * @throws ReflectiveOperationException If a reflective operation error has
   *           occurred.
   */
  @SuppressWarnings("resource")
  private static Tracer loadTracer() throws IOException, ReflectiveOperationException {
    if (logger.isLoggable(Level.FINE))
      logger.fine("\n<<<<<<<<<<<<<<<<<<<< Loading Tracer Plugin >>>>>>>>>>>>>>>>>>>>>\n");

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
        logger.fine("\n>>>>>>>>>>>>>>>>>>>> Loaded Tracer Plugin <<<<<<<<<<<<<<<<<<<<<<\n");
    }
  }

  /**
   * Links the {@link AgentRule} at the specified {@code index} to the provided
   * target {@link ClassLoader classLoader}.
   *
   * @param index The index identifying the Instrumentation Rule in the list of
   *          Instrumentation Rules in {@link PluginsClassLoader#getFiles()} to
   *          be linked to the provided target {@link ClassLoader classLoader}.
   * @param classLoader The target {@link ClassLoader classLoader} to which the
   *          Instrumentation Rule at the specified index is to be linked.
   * @return Whether the Instrumentation Rule was compatible and was
   *         successfully linked to the provided target {@link ClassLoader
   *         classLoader}.
   */
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
        final PluginManifest pluginManifest = pluginManifestDirectory.get(pluginFile);
        logger.finer("SpecialAgent.linkRule(\"" + pluginManifest.name + "\"[" + index + "], " + AssembleUtil.getNameId(classLoader) + "): compatible = " + compatible + " [cached]");
      }

      return true;
    }

    // Find the Plugin File (identified by index passed to this method)
    final File pluginFile = pluginsClassLoader.getFiles()[index];
    final PluginManifest pluginManifest = pluginManifestDirectory.get(pluginFile);
    if (logger.isLoggable(Level.FINER))
      logger.finer("SpecialAgent.linkRule(\"" + pluginManifest.name + "\"[" + index + "], " + AssembleUtil.getNameId(classLoader) + "): compatible = " + compatible + ", RulePath: " + pluginFile);

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
        logger.finer("[" + pluginManifest.name + "] Target class loader is: null (bootstrap)");

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
        logger.finer("[" + pluginManifest.name + "] Target class loader is: " + AssembleUtil.getNameId(classLoader) + " (system)");

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

    // If the callstack is coming from ClassLoader#defineClass, defer injection of
    // classes, as injection from ClassLoader#defineClass may lead to LinkageError
    // (duplicate class definition), or a ClassCircularityError.
    for (final StackTraceElement stackTraceElement : Thread.currentThread().getStackTrace()) {
      if (DEFINE_CLASS.equals(stackTraceElement.getClassName() + "." + stackTraceElement.getMethodName())) {
        if (logger.isLoggable(Level.FINER))
          logger.finer("[" + pluginManifest.name + "] Injection of instrumentation classes deferred");

        return true;
      }
    }

    // Otherwise, inject the classes immediately.
    if (logger.isLoggable(Level.FINER))
      logger.finer("[" + pluginManifest.name + "] Injection of instrumentation classes called");

    ruleClassLoader.inject(classLoader);
    return true;
  }

  /**
   * Invoke the specified arguments applied to the provided {@link QuadFunction
   * function}. The contract of this method is as follows:
   * <ol>
   * <li>Starting with the provided {@link ClassLoader targetLoader} set as
   * {@code contextLoader}, the method attempts to find associated
   * {@link RuleClassLoader}s from {@link #classLoaderToRuleClassLoader}.</li>
   * <li>If associated {@link RuleClassLoader}s are found, the provided
   * {@link QuadFunction function} is applied to the
   * {@link RuleClassLoader}s.</li>
   * <li>If associated the return of the {@link QuadFunction function}
   * invocation is not null, this method returns this value.</li>
   * <li>Otherwise, this method repeats this sequence of steps on
   * {@code contextLoader = contextLoader.getParent()}.</li>
   * <li>The default return of this method is {@code null}.</li>
   * </ol>
   *
   * @param <R> The type parameter for the return.
   * @param <T> The type parameter for the {@link Throwable}.
   * @param name The name of the class or resource on which to perform the
   *          {@link QuadFunction function}.
   * @param targetLoader The target {@link ClassLoader} on which to perform the
   *          {@link QuadFunction function}.
   * @param function The {@link QuadFunction} implementing the operation to
   *          invoke.
   * @return The first non-null value returned from the invocation of the
   *         provided {@link QuadFunction function}, or {@code null} by default.
   * @throws T A type-specified {@link Throwable} if an error has occurred.
   */
  private static <R,T extends Throwable>R invoke(final String name, final ClassLoader targetLoader, final QuadFunction<ClassLoader,String,List<RuleClassLoader>,ClassLoader,R,T> function) throws T {
    // Check if the class loader matches a ruleClassLoader
    List<RuleClassLoader> ruleClassLoaders;
    for (ClassLoader contextLoader = targetLoader; contextLoader != null; contextLoader = contextLoader.getParent()) {
      ruleClassLoaders = classLoaderToRuleClassLoader.get(contextLoader);
      if (ruleClassLoaders != null) {
        final R r = function.apply(targetLoader, name, ruleClassLoaders, contextLoader);
        if (r != null)
          return r;
      }
    }

    return null;
  }

  /**
   * A {@link QuadFunction function} for the injection of classes from
   * {@link RuleClassLoader}s to a target {@link ClassLoader}. This function
   * returns a non-null value by default, to prevent the injection to be
   * attempted for each parent class loader.
   */
  private static final QuadFunction<ClassLoader,String,List<RuleClassLoader>,ClassLoader,Boolean,RuntimeException> inject = new QuadFunction<ClassLoader,String,List<RuleClassLoader>,ClassLoader,Boolean,RuntimeException>() {
    @Override
    public Boolean apply(final ClassLoader targetLoader, final String name, final List<RuleClassLoader> ruleClassLoaders, final ClassLoader contextLoader) {
      for (int i = 0; i < ruleClassLoaders.size(); ++i) {
        final RuleClassLoader ruleClassLoader = ruleClassLoaders.get(i);
        ruleClassLoader.inject(contextLoader);
      }

      return Boolean.TRUE;
    }
  };

  /**
   * Injects classes of the {@code RuleClassLoader} into the specified
   * {@link ClassLoader classLoader} by calling
   * {@link Class#forName(String,boolean,ClassLoader)} on all classes belonging
   * to this class loader to be attempted to be found by the specified
   * {@link ClassLoader classLoader}. A side-effect of this procedure is that is
   * will load all dependent classes that are also needed to be loaded, which
   * may belong to a different class loader (i.e. the parent, or parent's
   * parent, and so on).
   *
   * @param classLoader The target {@code ClassLoader} of the injection.
   */
  public static void inject(final ClassLoader classLoader) {
    if (logger.isLoggable(Level.FINEST))
      logger.finest(">>>>>>>> inject(" + AssembleUtil.getNameId(classLoader) + ")");

    invoke(null, classLoader, inject);
  }

  /**
   * A {@link QuadFunction function} that finds a bytecode {@code byte[]} of the
   * class by the specified {@code name} in the provided {@link ClassLoader
   * targetLoader}. The {@link ClassLoader contextLoader} refers to each parent
   * class loader as provided to this function from
   * {@link #invoke(String,ClassLoader,QuadFunction)} until it receives the
   * first non-null return value. The non-null return value of this function is
   * the bytecode {@code byte[]} of the class by the specified {@code name}.
   */
  private static final QuadFunction<ClassLoader,String,List<RuleClassLoader>,ClassLoader,byte[],RuntimeException> findClass = new QuadFunction<ClassLoader,String,List<RuleClassLoader>,ClassLoader,byte[],RuntimeException>() {
    @Override
    public byte[] apply(final ClassLoader targetLoader, final String name, final List<RuleClassLoader> ruleClassLoaders, final ClassLoader contextLoader) {
      final String resourceName = AssembleUtil.classNameToResource(name);
      for (int i = 0; i < ruleClassLoaders.size(); ++i) {
        final RuleClassLoader ruleClassLoader = ruleClassLoaders.get(i);
        if (ruleClassLoader.isClosed(contextLoader)) {
          if (logger.isLoggable(Level.FINEST))
            logger.finest(">>>>>>>> findClass(" + AssembleUtil.getNameId(targetLoader) + ", \"" + name + "\"): CLOSED");

          continue;
        }

        final URL resourceUrl = ruleClassLoader.getResource(resourceName);
        if (resourceUrl == null)
          continue;

        // Return the resource's bytes
        final byte[] bytecode = AssembleUtil.readBytes(resourceUrl);
        if (logger.isLoggable(Level.FINEST))
          logger.finest(">>>>>>>> findClass(" + AssembleUtil.getNameId(targetLoader) + ", \"" + name + "\"): BYTECODE " + (bytecode != null ? "!" : "=") + "= null");

        return bytecode;
      }

      if (logger.isLoggable(Level.FINEST))
        logger.finest(">>>>>>>> findClass(" + AssembleUtil.getNameId(targetLoader) + ", \"" + name + "\"): Not found in " + ruleClassLoaders.size() + " RuleClassLoader(s)");

      return null;
    }
  };

  /**
   * Returns the bytecode of the {@code Class} by the name of {@code name}, if
   * the {@code classLoader} matched a rule {@code ClassLoader} that contains
   * OpenTracing instrumentation classes intended to be loaded into
   * {@code classLoader}. This method is called by the
   * {@link ClassLoaderAgentRule}. This method returns {@code null} if it cannot
   * locate the bytecode for the requested {@code Class} in the inheritance
   * chain of parent class loaders starting with the provided {@link ClassLoader
   * classLoader}, or if it has already been called for {@code classLoader} and
   * {@code name}.
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

  /**
   * A {@link QuadFunction function} that finds an {@link URL} of the resource
   * by the specified {@code name} in the provided {@link ClassLoader
   * targetLoader}. The {@link ClassLoader contextLoader} refers to each parent
   * class loader as provided to this function from
   * {@link #invoke(String,ClassLoader,QuadFunction)} until it receives the
   * first non-null return value. The non-null return value of this function is
   * the {@link URL} of the resource by the specified {@code name}.
   */
  private static final QuadFunction<ClassLoader,String,List<RuleClassLoader>,ClassLoader,URL,RuntimeException> findResource = new QuadFunction<ClassLoader,String,List<RuleClassLoader>,ClassLoader,URL,RuntimeException>() {
    @Override
    public URL apply(final ClassLoader targetLoader, final String name, final List<RuleClassLoader> ruleClassLoaders, final ClassLoader contextLoader) {
      for (int i = 0; i < ruleClassLoaders.size(); ++i) {
        final RuleClassLoader ruleClassLoader = ruleClassLoaders.get(i);
        if (ruleClassLoader.isClosed(contextLoader))
          continue;

        final URL resource = ruleClassLoader.findResource(name);
        if (resource != null)
          return resource;
      }

      return null;
    }
  };

  /**
   * Returns the resource {@link URL} by the name of {@code name}, if the
   * {@code classLoader} matched a rule {@code ClassLoader} that contains
   * OpenTracing instrumentation classes intended to be loaded into
   * {@code classLoader}. This method is called by the
   * {@link ClassLoaderAgentRule}. This method returns {@code null} if it cannot
   * locate the bytecode for the requested {@code Class} in the inheritance
   * chain of parent class loaders starting with the provided {@link ClassLoader
   * classLoader}, or if it has already been called for {@code classLoader} and
   * {@code name}.
   *
   * @param classLoader The {@code ClassLoader} to match to a
   *          {@link RuleClassLoader} that contains Instrumentation Plugin
   *          resources intended to be loaded via {@code classLoader}.
   * @param name The name of the resource to be found.
   * @return The resource {@link URL} by the name of {@code name}, or
   *         {@code null} if this method has already been called for
   *         {@code classLoader} and {@code name}.
   */
  public static URL findResource(final ClassLoader classLoader, final String name) {
    if (logger.isLoggable(Level.FINEST))
      logger.finest(">>>>>>>> findResource(" + AssembleUtil.getNameId(classLoader) + ", \"" + name + "\")");

    return invoke(name, classLoader, findResource);
  }

  /**
   * A {@link QuadFunction function} that finds an {@link Enumeration
   * Enumeration&lt;URL&gt;} of the resources by the specified {@code name} in
   * the provided {@link ClassLoader targetLoader}. The {@link ClassLoader
   * contextLoader} refers to each parent class loader as provided to this
   * function from {@link #invoke(String,ClassLoader,QuadFunction)} until it
   * receives the first non-null return value. The non-null return value of this
   * function is the {@link Enumeration Enumeration&lt;URL&gt;} of the resources
   * by the specified {@code name}.
   */
  private static final QuadFunction<ClassLoader,String,List<RuleClassLoader>,ClassLoader,Enumeration<URL>,IOException> findResources = new QuadFunction<ClassLoader,String,List<RuleClassLoader>,ClassLoader,Enumeration<URL>,IOException>() {
    @Override
    public Enumeration<URL> apply(final ClassLoader targetLoader, final String name, final List<RuleClassLoader> ruleClassLoaders, final ClassLoader contextLoader) throws IOException {
      for (int i = 0; i < ruleClassLoaders.size(); ++i) {
        final RuleClassLoader ruleClassLoader = ruleClassLoaders.get(i);
        if (ruleClassLoader.isClosed(contextLoader))
          continue;

        final Enumeration<URL> resources = ruleClassLoader.findResources(name);
        if (resources.hasMoreElements())
          return resources;
      }

      return null;
    }
  };

  /**
   * Returns the {@link Enumeration Enumeration&lt;URL&gt;} of resources by the
   * name of {@code name}, if the {@code classLoader} matched a rule
   * {@code ClassLoader} that contains OpenTracing instrumentation classes
   * intended to be loaded into {@code classLoader}. This method is called by
   * the {@link ClassLoaderAgentRule}. This method returns {@code null} if it
   * cannot locate the bytecode for the requested {@code Class} in the
   * inheritance chain of parent class loaders starting with the provided
   * {@link ClassLoader classLoader}, or if it has already been called for
   * {@code classLoader} and {@code name}.
   *
   * @param classLoader The {@code ClassLoader} to match to a
   *          {@link RuleClassLoader} that contains Instrumentation Plugin
   *          resources intended to be loaded via {@code classLoader}.
   * @param name The name of the resource to be found.
   * @return The {@link Enumeration Enumeration&lt;URL&gt;} of resources by the
   *         name of {@code name}, or {@code null} if this method has already
   *         been called for {@code classLoader} and {@code name}.
   * @throws IOException If an I/O error has occurred.
   */
  public static Enumeration<URL> findResources(final ClassLoader classLoader, final String name) throws IOException {
    if (logger.isLoggable(Level.FINEST))
      logger.finest(">>>>>>>> findResources(" + AssembleUtil.getNameId(classLoader) + ", \"" + name + "\")");

    return invoke(name, classLoader, findResources);
  }
}