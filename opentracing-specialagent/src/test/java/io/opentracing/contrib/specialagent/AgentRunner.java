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

import static io.opentracing.contrib.specialagent.Constants.*;
import static org.junit.Assert.*;

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
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.Set;

import org.apache.maven.cli.MavenCli;
import org.apache.maven.model.Dependency;
import org.junit.Assert;
import org.junit.rules.TestRule;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.FrameworkField;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.TestClass;

/**
 * A JUnit runner that is designed to run tests for instrumentation rules that
 * have auto-instrumentation rules implemented as per the {@link AgentRule} API.
 * <p>
 * The {@link AgentRunner} uses ByteBuddy's self-attach methodology to obtain
 * the {@link Instrumentation} instance. This architecture allows tests with the
 * {@link org.junit.runner.RunWith @RunWith(AgentRunner.class)} annotation to be
 * run from any environment (i.e. from Maven's Surefire plugin, from an IDE, or
 * directly via JUnit itself).
 * <p>
 * The {@link AgentRunner} has a facility to "raise" the classes loaded for the
 * purpose of the test into an isolated {@link ClassLoader} (see
 * {@link Config#isolateClassLoader()}). This allows the test to ensure that
 * instrumentation is successful for classes that are loaded in a class loader
 * that is not the System or Bootstrap class loader.
 * <p>
 * The {@link AgentRunner} also has a facility to aide in debugging of the
 * runner's runtime Please refer to {@link Config}.
 *
 * @author Seva Safris
 */
public class AgentRunner extends BlockJUnit4ClassRunner {
  private static final Logger logger = Logger.getLogger(AgentRunner.class);
  private static final boolean debug;
  private static final boolean inSurefireTest;

  private static final File CWD = new File("").getAbsoluteFile();
  private static final File[] classpath;
  private static final String dependenciesTgf;
  private static final String localRepositoryPath;
  private static final Instrumentation inst;
  private static final Set<String> bootstrapClasses;

  static {
    System.setProperty(AGENT_RUNNER_ARG, "");
    final String sunJavaCommand = System.getProperty("sun.java.command");
    AssembleUtil.absorbProperties(sunJavaCommand);
    AssembleUtil.loadProperties();

    final String debugProperty = System.getProperty("debug");
    debug = debugProperty != null && !"false".equals(debugProperty);
    inSurefireTest = sunJavaCommand.contains("org.apache.maven.surefire");

    try {
      URL dependenciesUrl = Thread.currentThread().getContextClassLoader().getResource(DEPENDENCIES_TGF);
      if (dependenciesUrl == null) {
        logger.warning(DEPENDENCIES_TGF + " was not found: invoking `mvn process-classes`");
        System.setProperty("maven.multiModuleProjectDirectory", CWD.getParentFile().getParentFile().getAbsolutePath());
        new MavenCli().doMain(new String[] {"process-classes"}, CWD.getAbsolutePath(), System.out, System.err);
        final File dependenciesTgf = new File(CWD, "target/classes/" + DEPENDENCIES_TGF);
        if (!dependenciesTgf.exists())
          throw new ExceptionInInitializerError(DEPENDENCIES_TGF + " was not found: Please assert that `mvn generate-resources` executes successfully");

        dependenciesUrl = Thread.currentThread().getContextClassLoader().getResource(DEPENDENCIES_TGF);
      }

      classpath = AssembleUtil.classPathToFiles(System.getProperty("java.class.path"));
      dependenciesTgf = new String(AssembleUtil.readBytes(dependenciesUrl));
      localRepositoryPath = new String(Files.readAllBytes(new File(CWD, "target/localRepository.txt").toPath()));

      final File[] bootstrapDependencies = RuleUtil.filterRuleURLs(classpath, dependenciesTgf, false, "compile", "test");
      if (debug) {
        System.err.println("Bootstrap Dependencies:\n  " + AssembleUtil.toIndentedString(bootstrapDependencies).replace("\n", "\n  "));
        checkFilesExist(bootstrapDependencies);
      }

      inst = AgentRunnerBootstrap.install(bootstrapDependencies);

      bootstrapClasses = AgentUtil.getClassFiles(bootstrapDependencies);
    }
    catch (final IOException e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  private static void checkFilesExist(final File ... files) {
    if (files != null)
      for (final File file : files)
        assertTrue(file.exists());
  }

  /**
   * Loads the specified class in an isolated {@link URLClassLoader}. The class
   * loader will be initialized with the process classpath, and will be detached
   * from the System {@link ClassLoader}. This construct guarantees that any
   * {@code cls} passed to this function will be unable to resolve classes in
   * the System {@link ClassLoader}.
   * <p>
   * <i><b>Note:</b> If {@code cls} is present in the Bootstrap
   * {@link ClassLoader}, it will be resolved in the Bootstrap
   * {@link ClassLoader} instead of the {@link URLClassLoader} created by this
   * function.</i>
   *
   * @param testClass The test class to load in the {@link URLClassLoader}.
   * @param isolate Whether the returned class should belong to an isolated
   *          class loader or not.
   * @return The class loaded in the {@link URLClassLoader}.
   * @throws InitializationError If the specified class cannot be located by the
   *           {@link URLClassLoader}.
   * @throws InterruptedException If a required Maven subprocess is interrupted.
   */
  private static Class<?> loadClassInIsolatedClassLoader(final Class<?> testClass, final boolean isolate) throws InitializationError, InterruptedException {
    try {
      final File[] ruleDependencies = RuleUtil.filterRuleURLs(classpath, dependenciesTgf, true, "compile");
      final Set<String> ruleClasses = AgentUtil.getClassFiles(ruleDependencies);

      final Set<String> testAppClasses = AgentUtil.getClassFiles(new File(testClass.getProtectionDomain().getCodeSource().getLocation().getPath()));
      final File[] libTestDependencies = RuleUtil.filterRuleURLs(classpath, dependenciesTgf, true, "test");
      if (libTestDependencies != null)
        testAppClasses.addAll(AgentUtil.getClassFiles(libTestDependencies));

      final File[] libDependencies = RuleUtil.filterRuleURLs(classpath, dependenciesTgf, true, "provided");
      if (libDependencies != null)
        testAppClasses.addAll(AgentUtil.getClassFiles(libDependencies));

      final Set<Dependency> isoDependencies = AssembleUtil.selectDependenciesFromTgf(dependenciesTgf, false, "isolated");
      final File[] isoFiles = new File[isoDependencies.size()];
      final Iterator<Dependency> iterator = isoDependencies.iterator();
      for (int i = 0; iterator.hasNext(); ++i)
        isoFiles[i] = getPath(localRepositoryPath, iterator.next());

      final Set<String> isoClasses = AgentUtil.getClassFiles(isoFiles);

      if (debug) {
        System.err.println("Rule Dependencies:\n  " + AssembleUtil.toIndentedString(ruleDependencies).replace("\n", "\n  "));
        checkFilesExist(ruleDependencies);
        System.err.println("Lib Test Dependencies:\n  " + AssembleUtil.toIndentedString(libTestDependencies).replace("\n", "\n  "));
        checkFilesExist(libTestDependencies);
        System.err.println("Lib Dependencies:\n  " + AssembleUtil.toIndentedString(libDependencies).replace("\n", "\n  "));
        checkFilesExist(libDependencies);
        System.err.println("Iso Dependencies:\n  " + AssembleUtil.toIndentedString(isoFiles).replace("\n", "\n  "));
        checkFilesExist(isoFiles);
      }

      final ClassLoader parent = System.getProperty("java.version").startsWith("1.") ? null : (ClassLoader)ClassLoader.class.getMethod("getPlatformClassLoader").invoke(null);
      agentRunnerClassLoader = new AgentRunnerClassLoader(AssembleUtil.toURLs(classpath), ruleDependencies, AssembleUtil.toURLs(isoFiles), parent) {
        @Override
        protected Class<?> loadClass(final String name, final boolean resolve) throws ClassNotFoundException {
          final String resourceName = AssembleUtil.classNameToResource(name);
          if (bootstrapClasses.contains(resourceName))
            return BootProxyClassLoader.INSTANCE.loadClass(name, resolve);

          // Plugin classes must be unresolvable by this class loader, so they
          // can be loaded by {@link ClassLoaderAgentRule.FindClass#exit}.
          if (ruleClasses.contains(resourceName))
            return null;

          // Test classes must be resolvable by the classpath {@link URL URL[]} of
          // this {@code URLClassLoader}.
          // can be loaded by {@link ClassLoaderAgentRule.FindClass#exit}.
          if (testAppClasses.contains(resourceName))
            return isolate ? super.loadClass(name, resolve) : ClassLoader.getSystemClassLoader().loadClass(name);

          // Iso classes must be resolvable by the IsoClassLoader
          if (isoClasses.contains(resourceName))
            return isoClassLoader.loadClass(name);

          // All other classes belong to the system class loader.
          return ClassLoader.getSystemClassLoader().loadClass(name);
        }
      };

      final Class<?> classInClassLoader = agentRunnerClassLoader.loadClass(testClass.getName());
      Assert.assertNotNull("Test class is not resolvable in URLClassLoader: " + testClass.getName(), classInClassLoader);
      Assert.assertNotNull("Test class must not be resolvable in bootstrap class loader: " + testClass.getName(), classInClassLoader.getClassLoader());
      if (isolate)
        Assert.assertEquals("Class " + testClass.getName() + " should have been loaded by the IsoClassLoader", agentRunnerClassLoader, classInClassLoader.getClassLoader());
      else
        Assert.assertEquals("Class " + testClass.getName() + " should have been loaded by the system class loader", ClassLoader.getSystemClassLoader(), classInClassLoader.getClassLoader());

      return classInClassLoader;
    }
    catch (final ClassNotFoundException | IllegalAccessException | InvocationTargetException | IOException | NoSuchMethodException e) {
      throw new InitializationError(e);
    }
  }

  private static File getPath(final String localRepositoryPath, final Dependency dependency) {
    final File ideFile;
    if (!inSurefireTest && CWD.getParentFile().getName().equals("rule") && (ideFile = new File(CWD.getParentFile().getParentFile(), dependency.getArtifactId() + "/target/classes")).exists())
      return ideFile;

    return new File(MavenUtil.getPathOf(localRepositoryPath, dependency));
  }

  /**
   * Annotation to specify configuration parameters for {@link AgentRunner} test methods.
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
   * Annotation to specify configuration parameters for {@link AgentRunner}.
   */
  @Target(ElementType.TYPE)
  @Retention(RetentionPolicy.RUNTIME)
  public @interface Config {
    /**
     * The logging {@link Level}.
     * <p>
     * Default: {@link Level#WARNING}.
     *
     * @return The logging {@link Level}.
     */
    Level log() default Level.WARNING;

    /**
     * Events to be logged during re/transformation.
     * <p>
     * Default: <code>{Event.ERROR}</code>.
     *
     * @return Events to be logged during re/transformation.
     */
    Event[] events() default {Event.ERROR};

    /**
     * Whether <u>Static Deferred Attach</u> is to be enabled.
     * <p>
     * Default: <code>false</code>.
     *
     * @return Whether <u>Static Deferred Attach</u> is to be enabled.
     */
    boolean defer() default false;

    /**
     * System properties to be set.
     * <p>
     * Default: <code>{}</code>.
     *
     * @return System properties to be set.
     */
    String[] properties() default {};

    /**
     * Names of plugins (either instrumentation or tracer) to be disabled.
     * <p>
     * Default: <code>{}</code>.
     *
     * @return Names of plugins (either instrumentation or tracer) to be
     *         disabled.
     */
    String[] disable() default {};

    /**
     * Whether the plugin is to be run in verbose mode.
     * <p>
     * Default: <code>false</code>.
     *
     * @return Whether the plugin is to be run in verbose mode.
     */
    boolean verbose() default false;

    /**
     * Whether the tests are to be run in a class loader that is isolated from
     * the system class loader (i.e. a {@link ClassLoader} with a {@code null}
     * parent).
     * <p>
     * <blockquote><i><b>Important</b>: All attempts should be taken to avoid
     * setting this property to {@code false}.
     * <p>
     * It is important to note that this option should only be set to
     * {@code false} in special situations, such as if a test relies on an
     * integrated module that does not function properly if the class loader of
     * its classes is isolated.
     * <p>
     * If this property is set to {@code false}, the {@link AgentRunner} runtime
     * disables all testing that asserts proper functionality of the rule when
     * the 3rd-party library it is instrumenting is loaded in a class loader
     * that is _not_ the system class loader.
     * <p>
     * <ins>By disabling this facet of the {@link AgentRunner}, the test may
     * pass, but the rule may fail in real-world application.</ins>
     * <p>
     * If this property is set to {@code false}, the build will print a
     * <b>WARN</b>-level log message, to warn the developer that
     * {@code isolateClassLoader=false}.</i> </blockquote> Default:
     * {@code true}.
     *
     * @return Whether the tests are to be run in a class loader that is
     *         isolated from the system class loader (i.e. a {@link ClassLoader}
     *         with a {@code null} parent).
     */
    boolean isolateClassLoader() default true;
  }

  private static AgentRunnerClassLoader agentRunnerClassLoader;

  private final Adapter adapter;
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
   * Creates a new {@link AgentRunner} for the specified test class.
   *
   * @param testClass The test class.
   * @throws InitializationError If the test class is malformed, or if the
   *           specified class cannot be located by the URLClassLoader in the
   *           forked process.
   * @throws InterruptedException If a required Maven subprocess is interrupted.
   */
  public AgentRunner(final Class<?> testClass) throws InitializationError, InterruptedException {
    super(loadClassInIsolatedClassLoader(testClass, testClass.getAnnotation(Config.class) == null || testClass.getAnnotation(Config.class).isolateClassLoader()));
    this.config = testClass.getAnnotation(Config.class);
    String path = testClass.getProtectionDomain().getCodeSource().getLocation().getPath();
    if (path.endsWith("/test-classes/"))
      path = path.substring(0, path.length() - 13) + "classes/";

    this.pluginManifest = Objects.requireNonNull(PluginManifest.getPluginManifest(new File(path)));
    final Event[] events;
    final boolean isStaticDeferredAttach;
    if (config == null) {
      events = new Event[] {Event.ERROR};
      isStaticDeferredAttach = false;
    }
    else {
      for (int i = 0; i < config.properties().length; ++i) {
        if (config.properties()[i] == null)
          continue;

        final String property = config.properties()[i].trim();
        if (property.length() == 0)
          continue;

        final int eq = property.indexOf('=');
        if (eq > 0)
          System.setProperty(property.substring(0, eq).trim(), property.substring(eq + 1).trim());
        else
          System.setProperty(property, "");
      }

      setDisable(config.disable());
      if (config.verbose())
        setVerbose(true);

      isStaticDeferredAttach = config.defer();
      events = config.events();
      if (config.log() != Level.WARNING) {
        final String logLevelProperty = System.getProperty(Logger.LOG_LEVEL_PROPERTY);
        if (logLevelProperty != null) {
          logger.warning(Logger.LOG_LEVEL_PROPERTY + "=" + logLevelProperty + " system property is specified on command line, and @" + AgentRunner.class.getSimpleName() + "." + Config.class.getSimpleName() + ".log=" + config.log() + " is specified in " + testClass.getName());
        }

        if (logLevelProperty == null || config.log().ordinal() < Level.valueOf(logLevelProperty).ordinal()) {
          System.setProperty(Logger.LOG_LEVEL_PROPERTY, String.valueOf(config.log()));
          Logger.refreshLoggers();
        }
      }

      if (!config.isolateClassLoader())
        logger.warning("`isolateClassLoader=false`\nAll attempts should be taken to avoid setting `isolateClassLoader=false`");
    }

    System.setProperty(INIT_DEFER, String.valueOf(isStaticDeferredAttach));

    if (events != null && events.length > 0) {
      final String eventsProperty = System.getProperty(LOG_EVENTS_PROPERTY);
      if (eventsProperty != null) {
        logger.warning(LOG_EVENTS_PROPERTY + "=" + eventsProperty + " system property is specified on command line, and @" + AgentRunner.class.getSimpleName() + "." + Config.class.getSimpleName() + ".events=" + Arrays.toString(events) + " is specified in " + testClass.getName());
      }
      else {
        final StringBuilder builder = new StringBuilder();
        for (final Event event : events)
          builder.append(event).append(",");

        builder.setLength(builder.length() - 1);
        System.setProperty(LOG_EVENTS_PROPERTY, builder.toString());
      }
    }

    try {
      SpecialAgent.premain(null, inst, agentRunnerClassLoader.ruleFiles, agentRunnerClassLoader.isoClassLoader);
    }
    catch (final Throwable e) {
      e.printStackTrace();
      throw new IllegalStateException(e.getMessage(), e.getCause());
    }

    this.adapter = ServiceLoader.load(Adapter.class).iterator().next();
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
   * Creates the {@link TestClass} object for this JUnit runner with the
   * specified test class.
   * <p>
   * This method has been overridden to retrofit the {@link FrameworkMethod}
   * objects.
   *
   * @param testClass The test class.
   * @return The {@link TestClass} object for this JUnit runner with the
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
   * Retrofits the specified {@link FrameworkMethod} to work with the forked
   * testing architecture of this runner.
   *
   * @param method The {@link FrameworkMethod} to retrofit.
   * @return The retrofitted {@link FrameworkMethod}.
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
          logger.finest("invokeExplosively [" + getName() + "](" + target + "<" + target.getClass().getClassLoader() + ">)");

        if (config == null || config.isolateClassLoader()) {
          final ClassLoader classLoader = isStatic() ? method.getDeclaringClass().getClassLoader() : target.getClass().getClassLoader();
          Assert.assertTrue("Method \"" + getName() + "\" should be executed in URLClassLoader", classLoader instanceof URLClassLoader);
        }

        final TestConfig testConfig = method.getMethod().getAnnotation(TestConfig.class);
        if (testConfig != null)
          setVerbose(testConfig.verbose());

        final Object object;
        if (method.getMethod().getParameterTypes().length == 1) {
//          System.out.println(method.getMethod().getParameterTypes()[0].getName() + " " + method.getMethod().getParameterTypes()[0].getClassLoader());
          final Object tracer = adapter.getAgentRunnerTracer();
//          System.out.println(tracer.getClass().getName() + " " + tracer.getClass().getClassLoader());
          object = super.invokeExplosively(target, tracer);
        }
        else {
          object = super.invokeExplosively(target);
        }

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