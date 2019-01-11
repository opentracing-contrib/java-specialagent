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
import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.instrument.Instrumentation;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import org.junit.Assert;
import org.junit.rules.TestRule;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.FrameworkField;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.TestClass;

import net.bytebuddy.agent.ByteBuddyAgent;

/**
 * A JUnit runner that is designed to run tests for instrumentation plugins that
 * have an associated {@code otarules.btm} file for automatic instrumentation
 * via Byteman.
 * <p>
 * The {@code AgentRunner} spawns a child process that executes the JUnit test
 * with an argument specifying {@code -javaagent}. The {@code AgentRunner}
 * establishes a socket-based communication with the child process to relay test
 * results. This architecture allows tests with the
 * {@code @RunWith(AgentRunner.class)} annotation to be run from any environment
 * (i.e. from Maven's Surefire plugin, from an IDE, or directly via JUnit
 * itself).
 * <p>
 * The {@code AgentRunner} also has a facility to "raise" the classes loaded for
 * the purpose of the test into an isolated {@code ClassLoader} (see
 * {@link Config#isolateClassLoader()}). This allows the test to ensure that
 * instrumentation is successful for classes that are loaded in a
 * {@code ClassLoader} that is not the System or Bootstrap {@code ClassLoader}.
 * <p>
 * The {@code AgentRunner} also has a facility to aide in debugging of the
 * runner's runtime, as well as Byteman's runtime (see {@link Config#debug()}
 * and {@link Config#verbose()}).
 *
 * @author Seva Safris
 */
public class AgentRunner extends BlockJUnit4ClassRunner {
  private static final Logger logger = Logger.getLogger(AgentRunner.class.getName());
  private static final Instrumentation inst;

  static {
    inst = ByteBuddyAgent.install();
    final URL url = Thread.currentThread().getContextClassLoader().getResource("/META-INF/opentracing-specialagent");
    System.out.println(url);
    try {
      final File zip = Util.zip(new File("/Users/seva/Work/opentracing/src/java-specialagent/opentracing-specialagent/target/classes/"));
      zip.deleteOnExit();
      inst.appendToBootstrapClassLoaderSearch(new JarFile(zip));
    }
    catch (final IOException e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  /**
   * Annotation to specify configuration parameters for {@code AgentRunner}.
   */
  @Target(ElementType.TYPE)
  @Retention(RetentionPolicy.RUNTIME)
  public @interface Config {
    /**
     * @return Whether to set Java logging level to {@link Level#FINEST}.
     *         <p>
     *         Default: {@code false}.
     */
    boolean debug() default false;

    /**
     * @return Whether to activate Byteman verbose logging via
     *         {@code -Dorg.jboss.byteman.verbose}.
     *         <p>
     *         Default: {@code false}.
     */
    boolean verbose() default false;

    /**
     * @return Whether the tests should be run in a {@code ClassLoader} that is
     *         isolated from the system {@code ClassLoader}.
     *         <p>
     *         Default: {@code true}.
     */
    boolean isolateClassLoader() default true;

    /**
     * @return Which {@link Instrumenter} to use for the tests.
     *         <p>
     *         Default: {@link Instrumenter#BYTEBUDDY}.
     */
    Instrumenter instrumenter() default Instrumenter.BYTEBUDDY;
  }

  /**
   * Returns a classpath string that includes the specified set of classpath
   * paths in the {@code includes} parameter, and excludes the specified set of
   * classpath paths from the {@code excludes} parameter.
   *
   * @param includes The paths to include in the returned classpath.
   * @param excludes The paths to exclude from the returned classpath.
   * @return The classpath string that includes paths from {@code includes}, and
   *         excludes paths from {@code excludes}.
   */
  private static String buildClassPath(final Set<String> includes, final Set<String> excludes) {
    if (excludes != null)
      includes.removeAll(excludes);

    final StringBuilder builder = new StringBuilder();
    final Iterator<String> iterator = includes.iterator();
    for (int i = 0; iterator.hasNext(); ++i) {
      if (i > 0)
        builder.append(File.pathSeparatorChar);

      builder.append(iterator.next());
    }

    return builder.toString();
  }

  /**
   * Loads the specified class in an isolated {@code URLClassLoader}. The class
   * loader will be initialized with the process classpath, and will be detached
   * from the System {@code ClassLoader}. This construct guarantees that any
   * {@code cls} passed to this function will be unable to resolve classes in
   * the System {@code ClassLoader}.
   * <p>
   * <i><b>Note:</b> If {@code cls} is present in the Bootstrap
   * {@code ClassLoader}, it will be resolved in the Bootstrap
   * {@code ClassLoader} instead of the {@code URLClassLoader} created by this
   * function.</i>
   *
   * @param cls The {@code Class} to load in the {@code URLClassLoader}.
   * @return The class loaded in the {@code URLClassLoader}.
   * @throws InitializationError If the specified class cannot be located by the
   *           {@code URLClassLoader}.
   */
  private static Class<?> loadClassInURLClassLoader(final Class<?> cls) throws InitializationError {
    try {
      final URL dependenciesUrl = Thread.currentThread().getContextClassLoader().getResource("dependencies.tgf");
      final List<String> pluginPaths;
      if (dependenciesUrl != null) {
        final URL[] pluginUrls = Util.filterPluginURLs(Util.classPathToURLs(System.getProperty("java.class.path")), dependenciesUrl, false, "compile");
        pluginPaths = new ArrayList<>(pluginUrls.length);
        for (int i = 0; i < pluginUrls.length; ++i)
          pluginPaths.add(pluginUrls[i].getFile());
      }
      else {
        logger.warning("dependencies.tgf was not found! `mvn generate-resources` phase must be run for this file to be generated!");
        pluginPaths = null;
      }

      final Set<String> javaClassPath = Util.getJavaClassPath();
      if (logger.isLoggable(Level.FINEST))
        logger.finest("java.class.path:\n" + Util.toIndentedString(javaClassPath));

      // Use the whole java.class.path for the forked process, because any class
      // on the classpath may be used in the implementation of the test method.
      // The JARs with classes in the Boot-Path are already excluded due to their
      // provided scope.
      final String classpath = buildClassPath(javaClassPath, null);
      if (logger.isLoggable(Level.FINEST))
        logger.finest("ClassPath of forked process will be:\n  " + classpath.replace(File.pathSeparator, ",\n  "));

      if (logger.isLoggable(Level.FINEST))
        logger.finest("PluginsPath of forked process will be:\n" + Util.toIndentedString(pluginPaths));

      System.setProperty(SpecialAgent.PLUGIN_ARG, Util.toString(pluginPaths.toArray(), ":"));
      final URL[] testDependencies = Util.filterPluginURLs(Util.classPathToURLs(System.getProperty("java.class.path")), dependenciesUrl, true, "test", "provided");
      for (final URL testDependency : testDependencies)
        pluginPaths.add(testDependency.getPath());

      final String testClassesPath = cls.getProtectionDomain().getCodeSource().getLocation().getPath();
      final String classesPath = testClassesPath.endsWith(".jar") ? testClassesPath.replace(".jar", "-tests.jar") : testClassesPath.replace("/test-classes/", "/classes/");
      pluginPaths.add(testClassesPath);
      pluginPaths.add(classesPath);
      final Set<String> xxx = getAllFiles(pluginPaths, new Predicate<String>() {
        @Override
        public boolean test(final String t) {
          return t.endsWith(".class") && !t.contains("junit");
        }
      });

      xxx.add("io/opentracing/contrib/specialagent/AgentRunnerUtil.class");
      System.out.println(Util.toIndentedString(xxx));

      final URL[] libs = Util.classPathToURLs(System.getProperty("java.class.path"));
      // Special case for AgentRunnerITest, because it belongs to the same
      // classpath path as the AgentRunner
      final ClassLoader parent = ClassLoader.getSystemClassLoader();

      final URLClassLoader classLoader = new URLClassLoader(libs, new ClassLoader(parent) {
        @Override
        protected Class<?> loadClass(final String name, final boolean resolve) throws ClassNotFoundException {
          return xxx.contains(name.replace('.', '/').concat(".class")) ? null : super.loadClass(name, resolve);
        }
      });
      final Class<?> classInClassLoader = Class.forName(cls.getName(), false, classLoader);
      Assert.assertNotNull("Test class is not resolvable in URLClassLoader: " + cls.getName(), classInClassLoader);
      Assert.assertNotNull("Test class must not be resolvable in bootstrap class loader: " + cls.getName(), classInClassLoader.getClassLoader());
      Assert.assertEquals(URLClassLoader.class, classInClassLoader.getClassLoader().getClass());
//      Assert.assertNull(Rule.class.getClassLoader());
      return classInClassLoader;
    }
    catch (final ClassNotFoundException | IOException e) {
      throw new InitializationError(e);
    }
  }

  private static Set<String> getAllFiles(final List<String> pluginPaths, final Predicate<String> predicate) throws IOException {
    final Set<String> set = new HashSet<>();
    for (final String pluginPath : pluginPaths) {
      final File file = new File(pluginPath);
      if (pluginPath.endsWith(".jar")) {
        try (final JarFile jarFile = new JarFile(file)) {
          final Enumeration<JarEntry> entries = jarFile.entries();
          while (entries.hasMoreElements()) {
            final JarEntry entry = entries.nextElement();
            if (predicate.test(entry.getName()))
              set.add(entry.getName());
          }
        }
      }
      else {
        final Path path = file.toPath();
        Util.recurseDir(file, new Predicate<File>() {
          @Override
          public boolean test(final File t) {
            final String name = path.relativize(t.toPath()).toString();
            if (predicate.test(name))
              set.add(name);

            return true;
          }
        });
      }
    }

    return set;
  }


  private final Config config;
  private final URL loggingConfigFile;

  /**
   * Creates a new {@code AgentRunner} for the specified test class.
   *
   * @param cls The test class.
   * @throws InitializationError If the test class is malformed, or if the
   *           specified class cannot be located by the URLClassLoader in the
   *           forked process.
   */
  public AgentRunner(final Class<?> cls) throws InitializationError {
    super(cls.getAnnotation(Config.class) == null || cls.getAnnotation(Config.class).isolateClassLoader() ? loadClassInURLClassLoader(cls) : cls);
    this.config = cls.getAnnotation(Config.class);
    this.loggingConfigFile = config != null && config.debug() ? getClass().getResource("/logging.properties") : null;
    try {
      if (loggingConfigFile != null)
          LogManager.getLogManager().readConfiguration(loggingConfigFile.openStream());

      System.setProperty(SpecialAgent.INSTRUMENTER, config.instrumenter().name());

      if (config.verbose())
        System.setProperty("org.jboss.byteman.verbose", "true");

      SpecialAgent.premain(null, inst);
    }
    catch (final Throwable e) {
      e.printStackTrace();
    }
  }

  /**
   * Creates the {@code TestClass} object for this JUnit runner with the
   * specified test class.
   * <p>
   * This method has been overridden to retrofit the {@code FrameworkMethod}
   * objects.
   *
   * @param testClass The test class.
   * @return The {@code TestClass} object for this JUnit runner with the
   *         specified test class.
   */
  @Override
  protected TestClass createTestClass(final Class<?> testClass) {
    return new TestClass(testClass) {
      @Override
      public List<FrameworkMethod> getAnnotatedMethods(final Class<? extends Annotation> annotationClass) {
        final List<FrameworkMethod> retrofitted = new ArrayList<>();
        for (final FrameworkMethod method : super.getAnnotatedMethods(annotationClass))
          retrofitted.add(retrofitMethod(method, testClass.getClassLoader()));

        return Collections.unmodifiableList(retrofitted);
      }

      @Override
      protected void scanAnnotatedMembers(final Map<Class<? extends Annotation>,List<FrameworkMethod>> methodsForAnnotations, final Map<Class<? extends Annotation>,List<FrameworkField>> fieldsForAnnotations) {
        super.scanAnnotatedMembers(methodsForAnnotations, fieldsForAnnotations);
        for (final Map.Entry<Class<? extends Annotation>,List<FrameworkMethod>> entry : methodsForAnnotations.entrySet()) {
          final ListIterator<FrameworkMethod> iterator = entry.getValue().listIterator();
          while (iterator.hasNext())
            iterator.set(retrofitMethod(iterator.next(), testClass.getClassLoader()));
        }
      }
    };
  }

  /**
   * Retrofits the specified {@code FrameworkMethod} to work with the forked
   * testing architecture of this runner.
   *
   * @param method The {@code FrameworkMethod} to retrofit.
   * @return The retrofitted {@code FrameworkMethod}.
   */
  private FrameworkMethod retrofitMethod(final FrameworkMethod method, final ClassLoader classLoader) {
    return new FrameworkMethod(method.getMethod()) {
      @Override
      public void validatePublicVoidNoArg(final boolean isStatic, final List<Throwable> errors) {
        validatePublicVoid(isStatic, errors);
        if (method.getMethod().getParameterTypes().length > 1)
          errors.add(new Exception("Method " + method.getName() + " can declare no parameters, or one parameter of type: io.opentracing.mock.MockTracer"));
      }

      @Override
      public Object invokeExplosively(final Object target, final Object ... params) throws Throwable {
        if (logger.isLoggable(Level.FINEST))
          logger.finest("invokeExplosively [" + getName() + "](" + target + ")");

        if (config.isolateClassLoader()) {
          final ClassLoader classLoader = isStatic() ? method.getDeclaringClass().getClassLoader() : target.getClass().getClassLoader();
          Assert.assertEquals("Method " + getName() + " should be executed in URLClassLoader", URLClassLoader.class, classLoader == null ? null : classLoader.getClass());
        }

        final Class<?> cls = classLoader.loadClass("io.opentracing.contrib.specialagent.AgentRunnerUtil");
        return method.getMethod().getParameterTypes().length == 1 ? super.invokeExplosively(target, cls.getMethod("getTracer").invoke(null)) : super.invokeExplosively(target);
      }
    };
  }

  /**
   * Overridden because the stock implementation does not remove null values,
   * which ends up causing a NullPointerException later down a callstack.
   */
  @Override
  protected List<TestRule> getTestRules(final Object target) {
    final List<TestRule> rules = super.getTestRules(target);
    final ListIterator<TestRule> iterator = rules.listIterator(rules.size());
    while (iterator.hasPrevious())
      if (iterator.previous() == null)
        iterator.remove();

    return rules;
  }

  /**
   * @return The classpath path of the opentracing-specialagent.
   */
  protected String getAgentPath() {
    return SpecialAgent.class.getProtectionDomain().getCodeSource().getLocation().getFile();
  }
}