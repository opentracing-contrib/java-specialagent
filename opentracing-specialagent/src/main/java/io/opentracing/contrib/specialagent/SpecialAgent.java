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
import java.io.InputStream;
import java.lang.instrument.Instrumentation;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.sun.tools.attach.VirtualMachine;

/**
 * The SpecialAgent.
 *
 * @author Seva Safris
 */
public class SpecialAgent {
  private static final Logger logger = Logger.getLogger(SpecialAgent.class.getName());

  static final String PLUGIN_ARG = "io.opentracing.contrib.specialagent.plugins";
  static final String INSTRUMENTER = "io.opentracing.contrib.specialagent.instrumenter";

  private static final String DEPENDENCIES = "dependencies.tgf";

  private static final Map<ClassLoader,PluginClassLoader> classLoaderToPluginClassLoader = new IdentityHashMap<>();

  private static String agentArgs;
  private static AllPluginsClassLoader allPluginsClassLoader;

  private static final Instrumenter instrumenter;

  static {
    final String loggingConfig = System.getProperty("java.util.logging.config.file");
    if (loggingConfig != null) {
      try {
        LogManager.getLogManager().readConfiguration((loggingConfig.contains("file:/") ? new URL(loggingConfig) : new URL("file", "", loggingConfig)).openStream());
      }
      catch (final IOException e) {
        throw new ExceptionInInitializerError(e);
      }
    }

    final String instrumenterProperty = System.getProperty(INSTRUMENTER);
    instrumenter = instrumenterProperty == null ? Instrumenter.BYTEBUDDY : Instrumenter.valueOf(instrumenterProperty.toUpperCase());
  }

  private static Instrumentation instrumentation;

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

  public static void premain(final String agentArgs, final Instrumentation instrumentation) throws Exception {
    SpecialAgent.agentArgs = agentArgs;
    SpecialAgent.instrumentation = instrumentation;
    instrumenter.manager.premain(agentArgs, instrumentation);
  }

  public static void agentmain(final String agentArgs, final Instrumentation instrumentation) throws Exception {
    premain(agentArgs, instrumentation);
  }

  public static void initialize() {
    if (logger.isLoggable(Level.FINEST))
      logger.finest("Agent#initialize() java.class.path:\n  " + System.getProperty("java.class.path").replace(File.pathSeparator, "\n  "));

    final Set<URL> pluginJarUrls = Util.findJarResources("META-INF/opentracing-specialagent/");
    final boolean runningFromTest = pluginJarUrls.isEmpty();
    if (runningFromTest) {
      if (logger.isLoggable(Level.FINE))
        logger.fine("Must be running from a test, because no JARs were found under META-INF/opentracing-specialagent/");

      try {
        final Enumeration<URL> resources = instrumenter.manager.getResources();
        while (resources.hasMoreElements())
          pluginJarUrls.add(new URL(Util.getSourceLocation(resources.nextElement(), instrumenter.manager.file)));
      }
      catch (final IOException e) {
        throw new IllegalStateException(e);
      }

      final URL[] pluginPaths = Util.classPathToURLs(System.getProperty(PLUGIN_ARG));
      if (pluginPaths != null)
        for (final URL pluginPath : pluginPaths)
          pluginJarUrls.add(pluginPath);
    }

    if (logger.isLoggable(Level.FINE))
      logger.fine("Loading " + pluginJarUrls.size() + " plugin paths:\n" + Util.toIndentedString(pluginJarUrls));

    // Override parent ClassLoader methods to avoid delegation of resource
    // resolution to BootLoader
    allPluginsClassLoader = new AllPluginsClassLoader(pluginJarUrls);

    int count = loadDependencies(allPluginsClassLoader);
    if (runningFromTest)
      count += loadDependencies(ClassLoader.getSystemClassLoader());

    if (count == 0)
      logger.log(runningFromTest ? Level.WARNING : Level.SEVERE, "Could not find " + DEPENDENCIES + " in any plugin JARs");

    loadRules();
  }

  static class AllPluginsClassLoader extends URLClassLoader {
    private final Set<URL> urls;

    public AllPluginsClassLoader(final Set<URL> urls) {
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
   * Loads all dependencies.tgf files, and cross-links the dependency references
   * with the matching plugin JARs.
   *
   * @param classLoader The {@code ClassLoader} in which to search for
   *          dependencies.tgf files.
   * @return The number of loaded dependencies.tgf files.
   */
  private static int loadDependencies(final ClassLoader classLoader) {
    int count = 0;
    try {
      final Enumeration<URL> enumeration = classLoader.getResources(DEPENDENCIES);
      final Set<String> dependencyUrls = new HashSet<>();

      while (enumeration.hasMoreElements()) {
        final URL dependencyUrl = enumeration.nextElement();
        if (dependencyUrls.contains(dependencyUrl.toString()))
          continue;

        dependencyUrls.add(dependencyUrl.toString());
        if (logger.isLoggable(Level.FINEST))
          logger.finest("Found " + DEPENDENCIES + ": " + dependencyUrl + " " + Util.getIdentityCode(dependencyUrl));

        final URL jarUrl = new URL(Util.getSourceLocation(dependencyUrl, DEPENDENCIES));
        final URL[] dependencies = Util.filterPluginURLs(allPluginsClassLoader.getURLs(), dependencyUrl);
        boolean foundReference = false;
        for (final URL dependency : dependencies) {
          if (allPluginsClassLoader.containsPath(dependency)) {
            // When run from a test, it may happen that both the "allPluginsClassLoader"
            // and SystemClassLoader have the same path, leading to the same dependencies.tgf
            // file to be processed twice. This check asserts the previously registered
            // dependencies are correct.
            foundReference =  true;
            final URL[] registered = pluginToDependencies.get(dependency);
            if (registered != null) {
              if (registered == pluginToDependencies.get(jarUrl))
                continue;

              throw new IllegalStateException("Dependencies already registered for " + dependency + " Are there multiple plugin JARs with " + DEPENDENCIES + " referencing the same plugin JAR? Offending JAR: " + jarUrl);
            }

            if (logger.isLoggable(Level.FINEST))
              logger.finest("Registering dependencies for " + jarUrl + " and " + dependency + ":\n" + Util.toIndentedString(dependencies));

            ++count;
            pluginToDependencies.put(jarUrl, dependencies);
            pluginToDependencies.put(dependency, dependencies);
          }
        }

        if (!foundReference)
          throw new IllegalStateException("Could not find a plugin JAR referenced in " + jarUrl + DEPENDENCIES + " from: \n" + Util.toIndentedString(dependencies));
      }
    }
    catch (final IOException e) {
      throw new IllegalStateException(e);
    }

    return count;
  }

  private static final Map<URL,URL[]> pluginToDependencies = new HashMap<>();

  /**
   * This method loads any OpenTracing Agent rules (otarules.btm) found as
   * resources within the supplied class loader.
   */
  private static void loadRules() {
    if (allPluginsClassLoader == null) {
      logger.severe("Attempt to load OpenTracing agent rules before allPluginsClassLoader initialized");
      return;
    }

    try {
      // Create map from plugin jar URL to its index in
      // allPluginsClassLoader.getURLs()
      final Map<String,Integer> pluginJarToIndex = new HashMap<>();
      for (int i = 0; i < allPluginsClassLoader.getURLs().length; ++i)
        pluginJarToIndex.put(allPluginsClassLoader.getURLs()[i].toString(), i);

      instrumenter.manager.loadRules(allPluginsClassLoader, pluginJarToIndex, agentArgs);
    }
    catch (final IOException e) {
      logger.log(Level.SEVERE, "Failed to load OpenTracing agent rules", e);
    }

    if (logger.isLoggable(Level.FINE))
      logger.fine("OpenTracing Agent rules loaded");
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
   * during the script retrofit in
   * {@link BytemanManager#retrofitScript(String,int)}.
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
   * @return {@code true} if the plugin at the specified index is compatible
   *         with its target classes in the invoking class's
   *         {@code ClassLoader}.
   * @see BytemanManager#retrofitScript(String,int)
   */
  public static boolean linkPlugin(final int index, final Class<?> cls, final Object[] args) {
    if (logger.isLoggable(Level.FINEST))
      logger.finest("linkPlugin(" + index + ", " + (cls != null ? cls.getName() + ".class" : "null") + ", " + Arrays.toString(args) + ")");

    // Get the class loader of the target class
    final Class<?> targetClass = args[0] != null ? args[0].getClass() : cls;
    final ClassLoader classLoader = targetClass.getClassLoader();
    return linkPlugin(index, classLoader);
  }

  @SuppressWarnings("resource")
  public static boolean linkPlugin(final int index, final ClassLoader classLoader) {
    instrumenter.manager.disableTriggers();
    try {
      // Find the Plugin Path (identified by index passed to this method)
      final URL pluginPath = allPluginsClassLoader.getURLs()[index];
      if (logger.isLoggable(Level.FINEST))
        logger.finest("  Plugin Path: " + pluginPath);

      // Now find all the paths that pluginPath depends on, by reading dependencies.tgf
      final URL[] pluginPaths = pluginToDependencies.get(pluginPath);
      if (pluginPaths == null)
        throw new IllegalStateException("No " + DEPENDENCIES + " was registered for: " + pluginPath);

      if (logger.isLoggable(Level.FINEST))
        logger.finest("new " + PluginClassLoader.class.getSimpleName() + "([\n" + Util.toIndentedString(pluginPaths) + "]\n, " + Util.getIdentityCode(classLoader) + ");");

      // Create an isolated (no parent class loader) URLClassLoader with the pluginPaths
      final PluginClassLoader pluginClassLoader = new PluginClassLoader(pluginPaths, classLoader);
      if (!pluginClassLoader.isCompatible(classLoader)) {
        try {
          pluginClassLoader.close();
        }
        catch (final IOException e) {
          logger.log(Level.WARNING, "Failed to close " + PluginClassLoader.class.getSimpleName() + ": " + Util.getIdentityCode(pluginClassLoader), e);
        }

        return false;
      }

      if (classLoader == null) {
        if (logger.isLoggable(Level.FINE))
          logger.fine("Target class loader is bootstrap, so adding plugin JARs to the bootstrap class loader directly");

        for (final URL path : pluginPaths) {
          try {
            instrumentation.appendToBootstrapClassLoaderSearch(new JarFile(path.getPath()));
          }
          catch (final IOException e) {
            logger.log(Level.SEVERE, "Failed to add path to bootstrap class loader: " + path.getPath(), e);
          }
        }
      }
      else if (classLoader == ClassLoader.getSystemClassLoader()) {
        if (logger.isLoggable(Level.FINE))
          logger.fine("Target class loader is system, so adding plugin JARs to the system class loader directly");

        for (final URL path : pluginPaths) {
          try {
            instrumentation.appendToSystemClassLoaderSearch(new JarFile(path.getPath()));
          }
          catch (final IOException e) {
            logger.log(Level.SEVERE, "Failed to add path to system class loader: " + path.getPath(), e);
          }
        }
      }

      // Associate the pluginClassLoader with the target class's classLoader
      classLoaderToPluginClassLoader.put(classLoader, pluginClassLoader);

      // Enable triggers to the classloader.btm script can execute
      instrumenter.manager.enableTriggers();

      // Call Class.forName(...) for each class in pluginClassLoader to load in
      // the caller's class loader
      for (final URL pathUrl : pluginClassLoader.getURLs()) {
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
    finally {
      instrumenter.manager.enableTriggers();
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
    instrumenter.manager.disableTriggers();
    try {
      if (logger.isLoggable(Level.FINEST))
        logger.finest(">>>>>>>> findClass(" + Util.getIdentityCode(classLoader) + ", \"" + name + "\")");

      // Check if the class loader matches a pluginClassLoader
      final PluginClassLoader pluginClassLoader = classLoaderToPluginClassLoader.get(classLoader);
      if (pluginClassLoader == null)
        return null;

      // Check that the resourceName has not already been retrieved by this method
      // (this may be a moot check, because the JVM won't call findClass() twice
      // for the same class)
      final String resourceName = name.replace('.', '/').concat(".class");
      if (pluginClassLoader.markFindResource(classLoader, resourceName))
        return null;

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
      instrumenter.manager.enableTriggers();
    }
  }

  public static URL findResource(final ClassLoader classLoader, final String name) {
    instrumenter.manager.disableTriggers();
    try {
      if (logger.isLoggable(Level.FINEST))
        logger.finest(">>>>>>>> findResource(" + Util.getIdentityCode(classLoader) + ", \"" + name + "\")");

      // Check if the class loader matches a pluginClassLoader
      final PluginClassLoader pluginClassLoader = classLoaderToPluginClassLoader.get(classLoader);
      return pluginClassLoader == null ? null : pluginClassLoader.findResource(name);
    }
    finally {
      instrumenter.manager.enableTriggers();
    }
  }

  public static Enumeration<URL> findResources(final ClassLoader classLoader, final String name) throws IOException {
    instrumenter.manager.disableTriggers();
    try {
      if (logger.isLoggable(Level.FINEST))
        logger.finest(">>>>>>>> findResources(" + Util.getIdentityCode(classLoader) + ", \"" + name + "\")");

      // Check if the class loader matches a pluginClassLoader
      final PluginClassLoader pluginClassLoader = classLoaderToPluginClassLoader.get(classLoader);
      if (pluginClassLoader == null)
        return null;

      final Enumeration<URL> resources = pluginClassLoader.findResources(name);
      return resources != null && resources.hasMoreElements() ? resources : null;
    }
    finally {
      instrumenter.manager.enableTriggers();
    }
  }
}