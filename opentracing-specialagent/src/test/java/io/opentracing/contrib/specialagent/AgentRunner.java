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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
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

import io.opentracing.Tracer;
import io.opentracing.contrib.tracerresolver.TracerResolver;
import io.opentracing.mock.MockTracer;
import io.opentracing.noop.NoopTracer;
import io.opentracing.util.GlobalTracer;
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

  private static JarFile createJarFileOfSource(final Class<?> cls) throws IOException {
    final String testClassesPath = cls.getProtectionDomain().getCodeSource().getLocation().getPath();
    if (testClassesPath.endsWith("-tests.jar"))
      return new JarFile(new File(testClassesPath.substring(0, testClassesPath.length() - 10) + ".jar"));

    if (testClassesPath.endsWith(".jar"))
      return new JarFile(new File(testClassesPath));

    if (testClassesPath.endsWith("/test-classes/")) {
      final File dir = new File(testClassesPath.substring(0, testClassesPath.length() - 14) + "/classes/");
      dir.deleteOnExit();
      return Util.createJarFile(dir);
    }

    if (testClassesPath.endsWith("classes/")) {
      final File dir = new File(testClassesPath.endsWith("/test-classes/") ? testClassesPath.substring(0, testClassesPath.length() - 14) + "/classes/" : testClassesPath);
      dir.deleteOnExit();
      return Util.createJarFile(dir);
    }

    throw new UnsupportedOperationException("Unsupported source path: " + testClassesPath);
  }

  static Instrumentation install() {
    if (inst != null)
      return inst;

    try {
      // FIXME: Can this be done in a better way?
      final JarFile jarFile1 = createJarFileOfSource(AgentRunner.class);
      final JarFile jarFile2 = createJarFileOfSource(AgentPlugin.class);
      final Instrumentation inst = ByteBuddyAgent.install();
      inst.appendToBootstrapClassLoaderSearch(jarFile1);
      inst.appendToBootstrapClassLoaderSearch(jarFile2);
      System.err.println("\n\n\n===============================================================\n\n\n");
      BootstrapAgent.premain(inst, jarFile1, jarFile2);
      return inst;
    }
    catch (final IOException e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  static {
    inst = install();
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
   * @param testClass The test class to load in the {@code URLClassLoader}.
   * @return The class loaded in the {@code URLClassLoader}.
   * @throws InitializationError If the specified class cannot be located by the
   *           {@code URLClassLoader}.
   */
  private static Class<?> loadClassInIsolatedClassLoader(final Class<?> testClass) throws InitializationError {
    try {
      final URL dependenciesUrl = Thread.currentThread().getContextClassLoader().getResource("dependencies.tgf");
      final String dependenciesTgf = dependenciesUrl == null ? null : new String(Util.readBytes(dependenciesUrl));
      final List<String> pluginPaths;
      final URL[] classpath = Util.classPathToURLs(System.getProperty("java.class.path"));
      if (dependenciesTgf != null) {
        final URL[] pluginUrls = Util.filterPluginURLs(classpath, dependenciesTgf, false, "compile");
        pluginPaths = new ArrayList<>(pluginUrls.length);
        for (int i = 0; i < pluginUrls.length; ++i)
          pluginPaths.add(pluginUrls[i].getFile());

        // Use the whole java.class.path for the forked process, because any class
        // on the classpath may be used in the implementation of the test method.
        // The JARs with classes in the Boot-Path are already excluded due to their
        // provided scope.
        if (logger.isLoggable(Level.FINEST))
          logger.finest("PluginsPath of forked process will be:\n" + Util.toIndentedString(pluginPaths));

        System.setProperty(SpecialAgent.PLUGIN_ARG, Util.toString(pluginPaths.toArray(), ":"));

        // Add scope={"test", "provided"}, optional=true to pluginPaths
        final URL[] testDependencies = Util.filterPluginURLs(classpath, dependenciesTgf, true, "test", "provided");
        for (final URL testDependency : testDependencies)
          pluginPaths.add(testDependency.getPath());
      }
      else {
        logger.warning("dependencies.tgf was not found! `mvn generate-resources` phase must be run for this file to be generated!");
        pluginPaths = null;
      }

      final String testClassesPath = testClass.getProtectionDomain().getCodeSource().getLocation().getPath();
      final String classesPath = testClassesPath.endsWith(".jar") ? testClassesPath.replace(".jar", "-tests.jar") : testClassesPath.replace("/test-classes/", "/classes/");
      pluginPaths.add(testClassesPath);
      pluginPaths.add(classesPath);
      final Set<String> isolatedClasses = TestUtil.getClassFiles(pluginPaths);

      // FIXME: Is there any way to properly reference this?
      isolatedClasses.add("io/opentracing/contrib/specialagent/AgentRunnerUtil.class");

      final URL[] libs = Util.classPathToURLs(System.getProperty("java.class.path"));
      // Special case for AgentRunnerITest, because it belongs to the same
      // classpath path as the AgentRunner

      final URLClassLoader classLoader = new URLClassLoader(libs, new ClassLoader(ClassLoader.getSystemClassLoader()) {
        private final ClassLoader bootstrapClassLoader = new URLClassLoader(new URL[0], null);

        @Override
        protected Class<?> loadClass(final String name, final boolean resolve) throws ClassNotFoundException {
          return isolatedClasses.contains(name.replace('.', '/').concat(".class")) ? bootstrapClassLoader.loadClass(name) : super.loadClass(name, resolve);
        }
      });

      final Class<?> classInClassLoader = Class.forName(testClass.getName(), false, classLoader);
      Assert.assertNotNull("Test class is not resolvable in URLClassLoader: " + testClass.getName(), classInClassLoader);
      Assert.assertNotNull("Test class must not be resolvable in bootstrap class loader: " + testClass.getName(), classInClassLoader.getClassLoader());
      Assert.assertEquals(URLClassLoader.class, classInClassLoader.getClassLoader().getClass());
      return classInClassLoader;
    }
    catch (final ClassNotFoundException | IOException e) {
      throw new InitializationError(e);
    }
  }

  private final Config config;
  private final URL loggingConfigFile;

  /**
   * Creates a new {@code AgentRunner} for the specified test class.
   *
   * @param testClass The test class.
   * @throws InitializationError If the test class is malformed, or if the
   *           specified class cannot be located by the URLClassLoader in the
   *           forked process.
   */
  public AgentRunner(final Class<?> testClass) throws InitializationError {
    super(testClass.getAnnotation(Config.class) == null || testClass.getAnnotation(Config.class).isolateClassLoader() ? loadClassInIsolatedClassLoader(testClass) : testClass);
    this.config = testClass.getAnnotation(Config.class);
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