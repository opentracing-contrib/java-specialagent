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
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.sun.tools.attach.VirtualMachine;

import io.opentracing.Tracer;
import io.opentracing.contrib.tracerresolver.TracerResolver;
import io.opentracing.mock.MockTracer;
import io.opentracing.util.GlobalTracer;

/**
 * The SpecialAgent.
 *
 * @author Seva Safris
 */
@SuppressWarnings("restriction")
public class SpecialAgent {
  private static final Logger logger = Logger.getLogger(SpecialAgent.class.getName());

  static final String AGENT_RUNNER_ARG = "sa.agentrunner";
  static final String RULE_PATH_ARG = "sa.rulepath";
  static final String TRACER_PROPERTY = "sa.tracer";
  static final String EVENTS_PROPERTY = "sa.log.events";
  static final String LOGGING_PROPERTY = "sa.log.level";

  static final String DEPENDENCIES_TGF = "dependencies.tgf";
  static final String TRACER_FACTORY = "META-INF/services/io.opentracing.contrib.tracerresolver.TracerFactory";

  private static final Map<ClassLoader,RuleClassLoader> classLoaderToRuleClassLoader = new IdentityHashMap<ClassLoader,RuleClassLoader>() {
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
    public RuleClassLoader get(final Object key) {
      RuleClassLoader value = super.get(key);
      if (value != null || !(key instanceof URLClassLoader))
        return value;

      final URLClassLoader urlClassLoader = (URLClassLoader)key;
      return urlClassLoader.getURLs().length > 0 || urlClassLoader.getParent() != null ? null : super.get(null);
    }
  };

  private static String agentArgs;
  private static AllPluginsClassLoader allPluginsClassLoader;

  // FIXME: ByteBuddy is now the only Instrumenter. Should this complexity be removed?
  private static final Instrumenter instrumenter = Instrumenter.BYTEBUDDY;

  static {
    final String configProperty = System.getProperty("config");
    try (
      final InputStream configInputStream = SpecialAgent.class.getResourceAsStream("/default.properties");
      final FileReader reader = configProperty == null ? null : new FileReader(new File(configProperty));
      final InputStream loggingInputStream = SpecialAgent.class.getResourceAsStream("/logging.properties");
    ) {
      final Properties properties = new Properties();

      // Load default config properties
      properties.load(configInputStream);

      // Load user config properties
      if (reader != null)
        properties.load(reader);

      // Set config properties as system properties
      for (final Map.Entry<Object,Object> entry : properties.entrySet())
        if (System.getProperty((String)entry.getKey()) == null)
          System.setProperty((String)entry.getKey(), (String)entry.getValue());

      // Load default logging properties
      LogManager.getLogManager().readConfiguration(loggingInputStream);

      // Load user logging properties
      final String loggingProperty = System.getProperty(LOGGING_PROPERTY);
      if (loggingProperty != null) {
        final Level level = Level.parse(loggingProperty);
        final Logger rootLogger = LogManager.getLogManager().getLogger("");
        rootLogger.setLevel(level);
        for (final Handler handler : rootLogger.getHandlers())
          handler.setLevel(level);
      }
    }
    catch (final IOException e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  private static Instrumentation inst;

  public static void main(final String[] args) throws Exception {
    if (args.length != 1) {
      System.err.println("Usage: <PID>");
      System.exit(1);
    }

    final VirtualMachine vm = VirtualMachine.attach(args[0]);
    final String agentPath = SpecialAgent.class.getProtectionDomain().getCodeSource().getLocation().getPath();
    try {
      vm.loadAgent(agentPath, null);
    }
    finally {
      vm.detach();
    }
  }

  /**
   * Entrypoint to load {@code SpecialAgent}.
   *
   * @param agentArgs Agent arguments.
   * @param inst The {@code Instrumentation}.
   * @throws Exception If an error has occurred.
   */
  public static void premain(final String agentArgs, final Instrumentation inst) throws Exception {
    BootLoaderAgent.premain(inst);
    SpecialAgent.agentArgs = agentArgs;
    SpecialAgent.inst = inst;
    instrumenter.manager.premain(null, inst);
  }

  public static void agentmain(final String agentArgs, final Instrumentation inst) throws Exception {
    premain(agentArgs, inst);
  }

  /**
   * Main initialization method for the {@code SpecialAgent}. This method is
   * called by the re/transformation {@link Manager} instance.
   */
  public static void initialize() {
    if (logger.isLoggable(Level.FINEST))
      logger.finest("Agent#initialize() java.class.path:\n  " + System.getProperty("java.class.path").replace(File.pathSeparator, "\n  "));

    final ArrayList<String> excludes = new ArrayList<>();
    for (final Map.Entry<Object,Object> property : System.getProperties().entrySet()) {
      final String key = (String)property.getKey();
      final String value = (String)property.getValue();
      if (key.endsWith(".enable") && !Boolean.parseBoolean(value)) {
        excludes.add(key.substring(0, key.length() - 7));
      }
    }

    // Add plugin JARs from META-INF/opentracing-specialagent/
    final Set<URL> pluginJarUrls = Util.findJarResources("META-INF/opentracing-specialagent/", excludes);
    if (logger.isLoggable(Level.FINER))
      logger.finer("Must be running from a test, because no JARs were found under META-INF/opentracing-specialagent/");

    try {
      // Add instrumentation rule JARs from system class loader
      final Enumeration<URL> instrumentationRules = instrumenter.manager.getResources();
      while (instrumentationRules.hasMoreElements())
        pluginJarUrls.add(Util.getSourceLocation(instrumentationRules.nextElement(), instrumenter.manager.file));
    }
    catch (final IOException e) {
      throw new IllegalStateException(e);
    }

    final URL[] rulePaths = Util.classPathToURLs(System.getProperty(RULE_PATH_ARG));
    if (rulePaths != null)
      for (final URL rulePath : rulePaths)
        pluginJarUrls.add(rulePath);

    if (logger.isLoggable(Level.FINER))
      logger.finer("Loading " + pluginJarUrls.size() + " rule paths:\n" + Util.toIndentedString(pluginJarUrls));

    allPluginsClassLoader = new AllPluginsClassLoader(pluginJarUrls);

    final int count = loadDependencies(allPluginsClassLoader) + loadDependencies(ClassLoader.getSystemClassLoader());
    if (count == 0)
      logger.log(Level.SEVERE, "Could not find " + DEPENDENCIES_TGF + " in any rule JARs");

    loadTracer();
    loadRules();
  }

  static class AllPluginsClassLoader extends URLClassLoader {
    private final Set<URL> urls;

    public AllPluginsClassLoader(final Set<URL> urls) {
      // Override parent ClassLoader methods to avoid delegation of resource
      // resolution to bootstrap class loader
      super(urls.toArray(new URL[urls.size()]), new ClassLoader(null) {
        // Overridden to ensure resources are not discovered in bootstrap class loader
        @Override
        public Enumeration<URL> getResources(final String name) throws IOException {
          return null;
        }
      });
      this.urls = urls;
    }

    public boolean containsPath(final URL url) {
      return urls.contains(url);
    }
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
          logger.finest("Found " + TRACER_FACTORY + ": <" + Util.getIdentityCode(url) + ">" + url);

        final String jarPath = Util.getSourceLocation(url, TRACER_FACTORY).getPath();
        final String artifactName = Util.getName(jarPath);
        final int dash = artifactName.indexOf('-');
        final String tracerName = dash == -1 ? artifactName : artifactName.substring(0, dash);
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
   * @return The number of loaded dependencies.tgf files.
   */
  private static int loadDependencies(final ClassLoader classLoader) {
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
          logger.finest("Found " + DEPENDENCIES_TGF + ": <" + Util.getIdentityCode(url) + ">" + url);

        final URL jarUrl = Util.getSourceLocation(url, DEPENDENCIES_TGF);
        final String dependenciesTgf = new String(Util.readBytes(url));
        final URL[] dependencies = Util.filterRuleURLs(allPluginsClassLoader.getURLs(), dependenciesTgf, false, "compile");
        if (dependencies == null)
          throw new UnsupportedOperationException("Unsupported " + DEPENDENCIES_TGF + " encountered. Please file an issue on https://github.com/opentracing-contrib/java-specialagent/");

        boolean foundReference = false;
        for (final URL dependency : dependencies) {
          if (allPluginsClassLoader.containsPath(dependency)) {
            // When run from a test, it may happen that both the "allPluginsClassLoader"
            // and SystemClassLoader have the same path, leading to the same dependencies.tgf
            // file to be processed twice. This check asserts the previously registered
            // dependencies are correct.
            foundReference =  true;
            final URL[] registered = ruleToDependencies.get(dependency);
            if (registered != null) {
              if (registered == ruleToDependencies.get(jarUrl))
                continue;

              throw new IllegalStateException("Dependencies already registered for " + dependency + " Are there multiple rule JARs with " + DEPENDENCIES_TGF + " referencing the same rule JAR? Offending JAR: " + jarUrl);
            }

            if (logger.isLoggable(Level.FINEST))
              logger.finest("Registering dependencies for " + jarUrl + " and " + dependency + ":\n" + Util.toIndentedString(dependencies));

            ++count;
            ruleToDependencies.put(jarUrl, dependencies);
            ruleToDependencies.put(dependency, dependencies);
          }
        }

        if (!foundReference)
          throw new IllegalStateException("Could not find a rule JAR referenced in " + jarUrl + DEPENDENCIES_TGF + " from: \n" + Util.toIndentedString(dependencies));
      }
    }
    catch (final IOException e) {
      throw new IllegalStateException(e);
    }

    return count;
  }

  private static final Map<URL,URL[]> ruleToDependencies = new HashMap<>();

  /**
   * This method loads any OpenTracing {@code AgentRule}s, delegated to the
   * instrumentation {@link Manager} in the runtime.
   */
  private static void loadRules() {
    if (allPluginsClassLoader == null) {
      logger.severe("Attempt to load OpenTracing agent rules before allPluginsClassLoader initialized");
      return;
    }

    try {
      // Create map from rule jar URL to its index in
      // allPluginsClassLoader.getURLs()
      final Map<URL,Integer> ruleJarToIndex = new HashMap<>();
      for (int i = 0; i < allPluginsClassLoader.getURLs().length; ++i)
        ruleJarToIndex.put(allPluginsClassLoader.getURLs()[i], i);

      instrumenter.manager.loadRules(allPluginsClassLoader, ruleJarToIndex, agentArgs, Util.digestEventsProperty(System.getProperty(EVENTS_PROPERTY)));
    }
    catch (final IOException e) {
      logger.log(Level.SEVERE, "Failed to load OpenTracing agent rules", e);
    }

    if (logger.isLoggable(Level.FINE))
      logger.fine("OpenTracing AgentRule(s) loaded");
  }

  private static boolean isAgentRunner() {
    return System.getProperty(AGENT_RUNNER_ARG) != null;
  }

  private static Tracer deferredTracer;

  static Tracer getDeferredTracer() {
    return deferredTracer;
  }

  public static void setDeferredTracer(Tracer deferredTracer) {
    SpecialAgent.deferredTracer = deferredTracer;
  }

  /**
   * Connects a Tracer Plugin to the runtime.
   */
  private static void loadTracer() {
    if (logger.isLoggable(Level.FINE))
      logger.fine("\n======================== Loading Tracer ========================\n");

    if (GlobalTracer.isRegistered()) {
      if (logger.isLoggable(Level.FINE))
        logger.fine("Tracer already registered with GlobalTracer");

      return;
    }

    final String tracerProperty = System.getProperty(TRACER_PROPERTY);
    if (tracerProperty == null)
      return;

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
        final URL tracerUrl = file.exists() ? new URL("file", null, file.getPath()) : findTracer(allPluginsClassLoader, tracerProperty);
        if (tracerUrl != null) {
          // If the desired tracer is in its own JAR file, or if this is not
          // running in an AgentRunner test (because in this case the tracer
          // is in a JAR also, which is inside the SpecialAgent JAR), then
          // isolate the tracer JAR in its own class loader.
          if (file.exists() || !isAgentRunner()) {
            final ClassLoader parent;
            if (System.getProperty("java.version").startsWith("1.")) {
              parent = null;
            }
            else {
              try {
                parent = (ClassLoader)ClassLoader.class.getMethod("getPlatformClassLoader").invoke(null);
              }
              catch (final IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
                throw new IllegalStateException(e);
              }
            }

            AgentRuleUtil.tracerClassLoader = new URLClassLoader(new URL[] {tracerUrl}, parent);
            Thread.currentThread().setContextClassLoader(AgentRuleUtil.tracerClassLoader);
          }
        }
        else if (findTracer(ClassLoader.getSystemClassLoader(), tracerProperty) == null) {
          throw new IllegalStateException("TRACER_PROPERTY=" + tracerProperty + " did not resolve to a tracer JAR or name");
        }
      }
      catch (final IOException e) {
        throw new IllegalStateException(e);
      }

      tracer = TracerResolver.resolveTracer();
      Thread.currentThread().setContextClassLoader(contextClassLoader);
    }

    if (tracer != null) {
      if (isAgentRunner())
        deferredTracer = tracer;
      else
        GlobalTracer.register(tracer);

      if (logger.isLoggable(Level.FINE))
        logger.fine("Tracer resolved and " + (isAgentRunner() ? "deferred to be registered" : "registered") + " with GlobalTracer:\n  " + tracer.getClass().getName() + " from " + (tracer.getClass().getProtectionDomain().getCodeSource() == null ? "null" : tracer.getClass().getProtectionDomain().getCodeSource().getLocation().getPath()));
    }
    else {
      logger.warning("Tracer NOT RESOLVED");
    }
  }

  /**
   * Callback that is used to load a class by the specified resource path into
   * the provided {@code ClassLoader}.
   */
  private static final BiPredicate<String,ClassLoader> loadClass = new BiPredicate<String,ClassLoader>() {
    @Override
    public boolean test(final String path, final ClassLoader classLoader) {
      if (path.endsWith(".class") && !path.startsWith("META-INF/") && !path.startsWith("module-info")) {
        try {
          Class.forName(path.substring(0, path.length() - 6).replace('/', '.'), false, classLoader);
        }
        catch (final ClassNotFoundException e) {
          logger.log(Level.SEVERE, "Failed to load class in " + classLoader, e);
        }
      }

      return true;
    }
  };

  @SuppressWarnings("resource")
  public static boolean linkRule(final int index, final ClassLoader classLoader) {
    // Find the Rule Path (identified by index passed to this method)
    final URL rulePath = allPluginsClassLoader.getURLs()[index];
    if (logger.isLoggable(Level.FINEST))
      logger.finest("  Rule Path: " + rulePath);

    // Now find all the paths that rulePath depends on, by reading dependencies.tgf
    final URL[] rulePaths = ruleToDependencies.get(rulePath);
    if (rulePaths == null)
      throw new IllegalStateException("No " + DEPENDENCIES_TGF + " was registered for: " + rulePath);

    if (logger.isLoggable(Level.FINEST))
      logger.finest("new " + RuleClassLoader.class.getSimpleName() + "([\n" + Util.toIndentedString(rulePaths) + "]\n, " + Util.getIdentityCode(classLoader) + ");");

    // Create an isolated (no parent class loader) URLClassLoader with the rulePaths
    final RuleClassLoader ruleClassLoader = new RuleClassLoader(rulePaths, classLoader);
    if (!ruleClassLoader.isCompatible(classLoader)) {
      try {
        ruleClassLoader.close();
      }
      catch (final IOException e) {
        logger.log(Level.WARNING, "Failed to close " + RuleClassLoader.class.getSimpleName() + ": " + Util.getIdentityCode(ruleClassLoader), e);
      }

      return false;
    }

    if (classLoader == null) {
      if (logger.isLoggable(Level.FINER))
        logger.finer("Target class loader is bootstrap, so adding rule JARs to the bootstrap class loader directly");

      for (final URL path : rulePaths) {
        try {
          final File file = new File(path.getPath());
          inst.appendToBootstrapClassLoaderSearch(file.isFile() ? new JarFile(file) : Util.createTempJarFile(file));
        }
        catch (final IOException e) {
          logger.log(Level.SEVERE, "Failed to add path to bootstrap class loader: " + path.getPath(), e);
        }
      }
    }
    else if (classLoader == ClassLoader.getSystemClassLoader()) {
      if (logger.isLoggable(Level.FINER))
        logger.finer("Target class loader is system, so adding rule JARs to the system class loader directly");

      for (final URL path : rulePaths) {
        try {
          final File file = new File(path.getPath());
          inst.appendToSystemClassLoaderSearch(file.isFile() ? new JarFile(file) : Util.createTempJarFile(file));
        }
        catch (final IOException e) {
          logger.log(Level.SEVERE, "Failed to add path to system class loader: " + path.getPath(), e);
        }
      }
    }

    // Associate the ruleClassLoader with the target class's classLoader
    classLoaderToRuleClassLoader.put(classLoader, ruleClassLoader);

    // Call Class.forName(...) for each class in ruleClassLoader to load in
    // the caller's class loader
    for (final URL pathUrl : ruleClassLoader.getURLs()) {
      if (pathUrl.toString().endsWith(".jar")) {
        try (final ZipInputStream zip = new ZipInputStream(pathUrl.openStream())) {
          for (ZipEntry entry; (entry = zip.getNextEntry()) != null; loadClass.test(entry.getName(), classLoader));
        }
        catch (final IOException e) {
          logger.log(Level.SEVERE, "Failed to read from JAR: " + pathUrl, e);
        }
      }
      else {
        final File dir = new File(URI.create(pathUrl.toString()));
        final Path path = dir.toPath();
        Util.recurseDir(dir, new Predicate<File>() {
          @Override
          public boolean test(final File file) {
            loadClass.test(path.relativize(file.toPath()).toString(), classLoader);
            return true;
          }
        });
      }
    }

    return true;
  }

  /**
   * Returns the bytecode of the {@code Class} by the name of {@code name}, if
   * the {@code classLoader} matched a rule {@code ClassLoader} that contains
   * OpenTracing instrumentation classes intended to be loaded into
   * {@code classLoader}. This method is called by the {@link ClassLoaderAgent}.
   * This method returns {@code null} if it cannot locate the bytecode for the
   * requested {@code Class}, or if it has already been called for
   * {@code classLoader} and {@code name}.
   *
   * @param classLoader The {@code ClassLoader} to match to a rule
   *          {@code ClassLoader} that contains OpenTracing instrumentation
   *          classes intended to be loaded into {@code classLoader}.
   * @param name The name of the {@code Class} to be found.
   * @return The bytecode of the {@code Class} by the name of {@code name}, or
   *         {@code null} if this method has already been called for
   *         {@code classLoader} and {@code name}.
   */
  public static byte[] findClass(final ClassLoader classLoader, final String name) {
    if (logger.isLoggable(Level.FINEST))
      logger.finest(">>>>>>>> findClass(" + Util.getIdentityCode(classLoader) + ", \"" + name + "\")");

    // Check if the class loader matches a ruleClassLoader
    final RuleClassLoader ruleClassLoader = classLoaderToRuleClassLoader.get(classLoader);
    if (ruleClassLoader == null)
      return null;

    // Check that the resourceName has not already been retrieved by this method
    // (this may be a moot check, because the JVM won't call findClass() twice
    // for the same class)
    final String resourceName = name.replace('.', '/').concat(".class");
    if (ruleClassLoader.markFindResource(classLoader, resourceName))
      return null;

    // Return the resource's bytes, or null if the resource does not exist in
    // ruleClassLoader
    final URL resource = ruleClassLoader.getResource(resourceName);
    return resource == null ? null : Util.readBytes(resource);
  }

  public static URL findResource(final ClassLoader classLoader, final String name) {
    if (logger.isLoggable(Level.FINEST))
      logger.finest(">>>>>>>> findResource(" + Util.getIdentityCode(classLoader) + ", \"" + name + "\")");

    // Check if the class loader matches a ruleClassLoader
    final RuleClassLoader ruleClassLoader = classLoaderToRuleClassLoader.get(classLoader);
    return ruleClassLoader == null ? null : ruleClassLoader.findResource(name);
  }

  public static Enumeration<URL> findResources(final ClassLoader classLoader, final String name) throws IOException {
    if (logger.isLoggable(Level.FINEST))
      logger.finest(">>>>>>>> findResources(" + Util.getIdentityCode(classLoader) + ", \"" + name + "\")");

    // Check if the class loader matches a ruleClassLoader
    final RuleClassLoader ruleClassLoader = classLoaderToRuleClassLoader.get(classLoader);
    if (ruleClassLoader == null)
      return null;

    final Enumeration<URL> resources = ruleClassLoader.findResources(name);
    return resources != null && resources.hasMoreElements() ? resources : null;
  }
}