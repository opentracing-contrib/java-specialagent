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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarFile;

import org.apache.maven.cli.MavenCli;
import org.junit.Assert;
import org.junit.rules.TestRule;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.FrameworkField;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.TestClass;

import io.opentracing.contrib.specialagent.Manager.Event;
import net.bytebuddy.agent.ByteBuddyAgent;

/**
 * A JUnit runner that is designed to run tests for instrumentation rules that
 * have auto-instrumentation rules implemented as per the {@link AgentRule}
 * API.
 * <p>
 * The {@code AgentRunner} uses ByteBuddy's self-attach methodology to obtain
 * the {@code Instrumentation} instance. This architecture allows tests with the
 * {@code @RunWith(AgentRunner.class)} annotation to be run from any environment
 * (i.e. from Maven's Surefire plugin, from an IDE, or directly via JUnit
 * itself).
 * <p>
 * The {@code AgentRunner} has a facility to "raise" the classes loaded for the
 * purpose of the test into an isolated {@code ClassLoader} (see
 * {@link Config#isolateClassLoader()}). This allows the test to ensure that
 * instrumentation is successful for classes that are loaded in a
 * class loader that is not the System or Bootstrap class loader.
 * <p>
 * The {@code AgentRunner} also has a facility to aide in debugging of the
 * runner's runtime Please refer to {@link Config}.
 *
 * @author Seva Safris
 */
public class AgentRunner extends BlockJUnit4ClassRunner {
  private static final Logger logger = Logger.getLogger(AgentRunner.class);
  private static final Instrumentation inst;

  private static JarFile createJarFileOfSource(final Class<?> cls) throws IOException {
    final String testClassesPath = cls.getProtectionDomain().getCodeSource().getLocation().getPath();
    if (testClassesPath.endsWith("-tests.jar"))
      return new JarFile(new File(testClassesPath.substring(0, testClassesPath.length() - 10) + ".jar"));

    if (testClassesPath.endsWith(".jar"))
      return new JarFile(new File(testClassesPath));

    if (testClassesPath.endsWith("/test-classes/"))
      return SpecialAgentUtil.createTempJarFile(new File(testClassesPath.substring(0, testClassesPath.length() - 14) + "/classes/"));

    if (testClassesPath.endsWith("/classes/"))
      return SpecialAgentUtil.createTempJarFile(new File(testClassesPath));

    throw new UnsupportedOperationException("Unsupported source path: " + testClassesPath);
  }

  private static JarFile appendSourceLocationToBootstrap(final Instrumentation inst, final Class<?> cls) throws IOException {
    final JarFile jarFile = createJarFileOfSource(cls);
    inst.appendToBootstrapClassLoaderSearch(jarFile);
    return jarFile;
  }

  static Instrumentation install() {
    if (inst != null)
      return inst;

    try {
      if (logger.isLoggable(Level.FINE))
        logger.fine("\n>>>>>>>>>>>>>>>>>>>>>>> Installing Agent <<<<<<<<<<<<<<<<<<<<<<<\n");

      final Instrumentation inst = ByteBuddyAgent.install();
      final JarFile jarFile0 = appendSourceLocationToBootstrap(inst, AgentRunner.class);
      if (logger.isLoggable(Level.FINE))
        logger.fine("\n================== Installing BootLoaderAgent ==================\n");

      BootLoaderAgent.premain(inst, jarFile0);
      return inst;
    }
    catch (final IOException e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  private static final File CWD = new File("").getAbsoluteFile();

  static {
    System.setProperty(SpecialAgentBase.AGENT_RUNNER_ARG, "");
    System.setProperty(SpecialAgentBase.INIT_DEFER, "false");
    final String sunJavaCommand = System.getProperty("sun.java.command");
    if (sunJavaCommand != null)
      AssembleUtil.absorbProperties(sunJavaCommand);

    SpecialAgentBase.loadProperties();
    inst = install();
  }

  /**
   * Annotation to specify configuration parameters for {@code AgentRunner} test methods.
   */
  @Target(ElementType.METHOD)
  @Retention(RetentionPolicy.RUNTIME)
  public @interface TestConfig {
    /**
     * @return Whether the plugin should run in verbose mode.
     *         <p>
     *         Default: <code>false</code>.
     */
    boolean verbose() default false;
  }

  /**
   * Annotation to specify configuration parameters for {@code AgentRunner}.
   */
  @Target(ElementType.TYPE)
  @Retention(RetentionPolicy.RUNTIME)
  public @interface Config {
    /**
     * @return Logging level.
     *         <p>
     *         Default: {@link Level#WARNING}.
     */
    Level log() default Level.WARNING;

    /**
     * @return Output re/transformer events.
     *         <p>
     *         Default: <code>{Event.ERROR}</code>.
     */
    Event[] events() default {Event.ERROR};

    /**
     * @return Names of plugins (either instrumentation or tracer) to disable in
     *         the test runtime.
     *         <p>
     *         Default: <code>{}</code>.
     */
    String[] disable() default {};

    /**
     * @return Whether the plugin should run in verbose mode.
     *         <p>
     *         Default: <code>false</code>.
     */
    boolean verbose() default false;

    /**
     * @return Whether the tests should be run in a class loader that is
     *         isolated from the system class loader (i.e. a {@code ClassLoader}
     *         with a {@code null} parent). <blockquote> <i><b>Important</b>:
     *         All attempts should be taken to avoid setting this property to
     *         {@code false}.
     *         <p>
     *         It is important to note that this option should only be set to
     *         {@code false} in special situations, such as if a test relies on
     *         an integrated module that does not function properly if the class
     *         loader of its classes is isolated.
     *         <p>
     *         If this property is set to {@code false}, the {@code AgentRunner}
     *         runtime disables all testing that asserts proper functionality of
     *         the rule when the 3rd-party library it is instrumenting is
     *         loaded in a class loader that is _not_ the system class loader.
     *         <p>
     *         <ins>By disabling this facet of the {@code AgentRunner}, the test
     *         may pass, but the rule may fail in real-world
     *         application.</ins>
     *         <p>
     *         If this property is set to {@code false}, the build will print a
     *         <b>WARN</b>-level log message, to warn the developer that
     *         {@code isolateClassLoader=false}.</i> </blockquote> Default:
     *         {@code true}.
     */
    boolean isolateClassLoader() default true;
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
   * @throws InterruptedException If a required Maven subprocess is interrupted.
   */
  private static Class<?> loadClassInIsolatedClassLoader(final Class<?> testClass) throws InitializationError, InterruptedException {
    try {
      URL dependenciesUrl = Thread.currentThread().getContextClassLoader().getResource(SpecialAgent.DEPENDENCIES_TGF);
      if (dependenciesUrl == null) {
        logger.warning(SpecialAgent.DEPENDENCIES_TGF + " was not found: invoking `mvn generate-resources`");
        System.setProperty("maven.multiModuleProjectDirectory", CWD.getParentFile().getParentFile().getAbsolutePath());
        new MavenCli().doMain(new String[] {"generate-resources"}, CWD.getAbsolutePath(), System.out, System.err);
        final File dependenciesTgf = new File(CWD, "target/generated-resources/" + SpecialAgent.DEPENDENCIES_TGF);
        if (dependenciesTgf.exists()) {
          Files.copy(dependenciesTgf.toPath(), new File(CWD, "target/classes/" + SpecialAgent.DEPENDENCIES_TGF).toPath());
          dependenciesUrl = Thread.currentThread().getContextClassLoader().getResource(SpecialAgent.DEPENDENCIES_TGF);
        }

        if (dependenciesUrl == null) {
          logger.severe(SpecialAgent.DEPENDENCIES_TGF + " was not found: Please assert that `mvn generate-resources` executes successfully");
          return Object.class;
        }
      }

      final List<File> rulePaths = findRulePaths(dependenciesUrl, true);
      final Set<String> pluginClasses = AgentUtil.getClassFiles(rulePaths);

      final List<File> testRulePaths = findRulePaths(dependenciesUrl, false);
      testRulePaths.add(0, new File(testClass.getProtectionDomain().getCodeSource().getLocation().getPath()));
      final Set<String> testClasses = AgentUtil.getClassFiles(testRulePaths);

      final File[] classpath = SpecialAgentUtil.classPathToFiles(System.getProperty("java.class.path"));
      final ClassLoader parent = System.getProperty("java.version").startsWith("1.") ? null : (ClassLoader)ClassLoader.class.getMethod("getPlatformClassLoader").invoke(null);
      final URLClassLoader isolatedClassLoader = new URLClassLoader(AssembleUtil.toURLs(classpath), parent) {
        @Override
        protected Class<?> loadClass(final String name, final boolean resolve) throws ClassNotFoundException {
          final String resourceName = AssembleUtil.classNameToResource(name);

          // Plugin classes must be unresolvable by this class loader, so they
          // can be loaded by {@link ClassLoaderAgentRule.FindClass#exit}.
          if (pluginClasses.contains(resourceName))
            return null;

          // Test classes must be resolvable by the classpath {@code URL[]} of
          // this {@code URLClassLoader}.
          // can be loaded by {@link ClassLoaderAgentRule.FindClass#exit}.
          if (testClasses.contains(resourceName))
            return super.loadClass(name, resolve);

          // All other classes belong to the system class loader.
          return ClassLoader.getSystemClassLoader().loadClass(name);
        }
      };

      final Class<?> classInClassLoader = Class.forName(testClass.getName(), false, isolatedClassLoader);
      Assert.assertNotNull("Test class is not resolvable in URLClassLoader: " + testClass.getName(), classInClassLoader);
      Assert.assertNotNull("Test class must not be resolvable in bootstrap class loader: " + testClass.getName(), classInClassLoader.getClassLoader());
      Assert.assertEquals(isolatedClassLoader, classInClassLoader.getClassLoader());
      return classInClassLoader;
    }
    catch (final ClassNotFoundException | IllegalAccessException | InvocationTargetException | IOException | NoSuchMethodException e) {
      throw new InitializationError(e);
    }
  }

  /**
   * Find the rule paths using the specified dependencies TGF {@code URL}.
   *
   * @param dependenciesUrl The {@code URL} pointing to the dependencies TGF
   *          file.
   * @return A list of the rule paths.
   * @throws IOException If an I/O error has occurred.
   * @throws NullPointerException If {@code dependenciesUrl} is null.
   */
  private static List<File> findRulePaths(final URL dependenciesUrl, final boolean isMain) throws IOException {
    final String dependenciesTgf = new String(AssembleUtil.readBytes(dependenciesUrl));

    final List<File> rulePaths = new ArrayList<>();
    final File[] classpath = SpecialAgentUtil.classPathToFiles(System.getProperty("java.class.path"));

    if (isMain) {
      final File[] dependencies = MavenUtil.filterRuleURLs(classpath, dependenciesTgf, false, "compile");
      if (dependencies == null)
        throw new UnsupportedOperationException("Unsupported " + SpecialAgent.DEPENDENCIES_TGF + " encountered. Please file an issue on https://github.com/opentracing-contrib/java-specialagent/");

      for (int i = 0; i < dependencies.length; ++i)
        rulePaths.add(dependencies[i]);
    }

    // Use the whole java.class.path for the forked process, because any class
    // on the classpath may be used in the implementation of the test method.
    // The JARs with classes in the Boot-Path are already excluded due to their
    // provided scope.
    if (isMain) {
      if (logger.isLoggable(Level.FINEST))
        logger.finest("rulePaths of runner will be:\n" + AssembleUtil.toIndentedString(rulePaths));

      System.setProperty(SpecialAgent.RULE_PATH_ARG, AssembleUtil.toString(rulePaths.toArray(), ":"));
    }

    if (!isMain) {
      // Add scope={"test", "provided"}, optional=true to rulePaths
      final File[] testDependencies = MavenUtil.filterRuleURLs(classpath, dependenciesTgf, true, "test", "provided");
      if (testDependencies == null)
        throw new UnsupportedOperationException("Unsupported " + SpecialAgent.DEPENDENCIES_TGF + " encountered. Please file an issue on https://github.com/opentracing-contrib/java-specialagent/");

      for (final File testDependency : testDependencies)
        rulePaths.add(testDependency);
    }

    return rulePaths;
  }

  private final Config config;
  private final PluginManifest pluginManifest;

  private void setVerbose(final boolean verbose) {
    System.setProperty("sa.instrumentation.plugin." + pluginManifest.name + ".verbose", String.valueOf(verbose));
  }

  private static void setDisable(final String[] disable) {
    for (final String name : disable) {
      System.setProperty("sa.instrumentation.plugin." + name + ".disable", "");
      System.setProperty("sa.tracer.plugin." + name + ".disable", "");
    }
  }

  /**
   * Creates a new {@code AgentRunner} for the specified test class.
   *
   * @param testClass The test class.
   * @throws InitializationError If the test class is malformed, or if the
   *           specified class cannot be located by the URLClassLoader in the
   *           forked process.
   * @throws InterruptedException If a required Maven subprocess is interrupted.
   */
  public AgentRunner(final Class<?> testClass) throws InitializationError, InterruptedException {
    super(testClass.getAnnotation(Config.class) == null || testClass.getAnnotation(Config.class).isolateClassLoader() ? loadClassInIsolatedClassLoader(testClass) : testClass);
    this.config = testClass.getAnnotation(Config.class);
    this.pluginManifest = PluginManifest.getPluginManifest(new File(testClass.getProtectionDomain().getCodeSource().getLocation().getPath()));
    final Event[] events;
    if (config == null) {
      events = new Event[] {Event.ERROR};
    }
    else {
      setDisable(config.disable());
      if (config.verbose())
        setVerbose(true);

      events = config.events();
      if (config.log() != Level.WARNING) {
        final String logLevelProperty = System.getProperty(Logger.LOG_LEVEL_PROPERTY);
        if (logLevelProperty != null) {
          logger.warning(Logger.LOG_LEVEL_PROPERTY + "=" + logLevelProperty + " system property is specified on command line, and @" + AgentRunner.class.getSimpleName() + "." + Config.class.getSimpleName() + ".log=" + config.log() + " is specified in " + testClass.getName());
        }

        if (logLevelProperty == null || config.log().ordinal() < Level.valueOf(logLevelProperty).ordinal()) {
          System.setProperty(Logger.LOG_LEVEL_PROPERTY, String.valueOf(config.log()));
          System.setProperty(Logger.LOG_REFRESH_PROPERTY, "true");
        }
      }

      if (!config.isolateClassLoader())
        logger.warning("`isolateClassLoader=false`\nAll attempts should be taken to avoid setting `isolateClassLoader=false`");
    }

    if (events != null && events.length > 0) {
      final String eventsProperty = System.getProperty(SpecialAgent.LOG_EVENTS_PROPERTY);
      if (eventsProperty != null) {
        logger.warning(SpecialAgent.LOG_EVENTS_PROPERTY + "=" + eventsProperty + " system property is specified on command line, and @" + AgentRunner.class.getSimpleName() + "." + Config.class.getSimpleName() + ".events=" + Arrays.toString(events) + " is specified in " + testClass.getName());
      }
      else {
        final StringBuilder builder = new StringBuilder();
        for (final Event event : events)
          builder.append(event).append(",");

        builder.setLength(builder.length() - 1);
        System.setProperty(SpecialAgent.LOG_EVENTS_PROPERTY, builder.toString());
      }
    }

    try {
      SpecialAgent.premain(null, inst);
    }
    catch (final Throwable e) {
      e.printStackTrace();
      throw new IllegalStateException(e.getMessage(), e.getCause());
    }
  }

  private int delta = Integer.MAX_VALUE;

  private static File getManifestFile() {
    return new File(new File(CWD, "target/classes"), UtilConstants.META_INF_TEST_MANIFEST);
  }

  @Override
  public void run(final RunNotifier notifier) {
    super.run(notifier);
    if (delta != 0)
      return;

    try {
      final String className = getTestClass().getName();
      final File manifestFile = getManifestFile();
      manifestFile.getParentFile().mkdirs();
      final Path path = manifestFile.toPath();
      final OpenOption openOption;
      if (Files.exists(path)) {
        // Check if the test class name is mentioned in the manifest
        try (final BufferedReader reader = new BufferedReader(new FileReader(manifestFile))) {
          String line;
          while ((line = reader.readLine()) != null)
            if (line.equals(className))
              return;
        }

        openOption = StandardOpenOption.APPEND;
      }
      else {
        openOption = StandardOpenOption.CREATE;
      }

      // Add the test class name to the manifest
      Files.write(path, (className + "\n").getBytes(), openOption);
    }
    catch (final IOException e) {
      throw new IllegalStateException(e);
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
          retrofitted.add(retrofitMethod(method));

        return Collections.unmodifiableList(retrofitted);
      }

      @Override
      protected void scanAnnotatedMembers(final Map<Class<? extends Annotation>,List<FrameworkMethod>> methodsForAnnotations, final Map<Class<? extends Annotation>,List<FrameworkField>> fieldsForAnnotations) {
        super.scanAnnotatedMembers(methodsForAnnotations, fieldsForAnnotations);
        for (final Map.Entry<Class<? extends Annotation>,List<FrameworkMethod>> entry : methodsForAnnotations.entrySet()) {
          final ListIterator<FrameworkMethod> iterator = entry.getValue().listIterator();
          while (iterator.hasNext())
            iterator.set(retrofitMethod(iterator.next()));
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
  private FrameworkMethod retrofitMethod(final FrameworkMethod method) {
    return new FrameworkMethod(method.getMethod()) {
      @Override
      public void validatePublicVoidNoArg(final boolean isStatic, final List<Throwable> errors) {
        validatePublicVoid(isStatic, errors);
        if (method.getMethod().getParameterTypes().length > 1)
          errors.add(new Exception("Method " + method.getName() + " can declare no parameters, or one parameter of type: io.opentracing.mock.MockTracer"));
      }

      @Override
      public Object invokeExplosively(final Object target, final Object ... params) throws Throwable {
        if (delta == Integer.MAX_VALUE)
          delta = 0;

        ++delta;
        if (logger.isLoggable(Level.FINEST))
          logger.finest("invokeExplosively [" + getName() + "](" + target + ")");

        if (config == null || config.isolateClassLoader()) {
          final ClassLoader classLoader = isStatic() ? method.getDeclaringClass().getClassLoader() : target.getClass().getClassLoader();
          Assert.assertTrue("Method \"" + getName() + "\" should be executed in URLClassLoader", classLoader instanceof URLClassLoader);
        }

        final TestConfig testConfig = method.getMethod().getAnnotation(TestConfig.class);
        if (testConfig != null)
          setVerbose(testConfig.verbose());

        final Object object = method.getMethod().getParameterTypes().length == 1 ? super.invokeExplosively(target, AgentRunnerUtil.getTracer()) : super.invokeExplosively(target);
        --delta;
        return object;
      }
    };
  }

  /**
   * Overridden because the stock implementation does not remove null values,
   * which ends up causing a NullPointerException later down a callstack.
   * {@inheritDoc}
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
}