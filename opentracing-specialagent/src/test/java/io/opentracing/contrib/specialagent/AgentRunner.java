package io.opentracing.contrib.specialagent;

import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.internal.runners.model.ReflectiveCallable;
import org.junit.runner.JUnitCore;
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

public class AgentRunner extends BlockJUnit4ClassRunner {
  private static final Logger logger = Logger.getLogger(AgentRunner.class.getName());
  private static final String PORT_ARG = "io.opentracing.contrib.specialagent.port";

  /**
   * Annotation to specify whether debug logging should be turned on or off.
   */
  @Target(ElementType.TYPE)
  @Retention(RetentionPolicy.RUNTIME)
  public static @interface Debug {
    boolean value();
  }

  /**
   * Returns a {@code Set} of string paths representing the classpath locations
   * of the specified classes.
   *
   * @param classes The classes for which to return a {@code Set} of classpath
   *          paths.
   * @return A {@code Set} of string paths representing the classpath locations
   *         of the specified classes.
   * @throws IOException If an I/O error has occurred.
   */
  private static Set<String> getLocations(final Class<?> ... classes) throws IOException {
    final ClassLoader classLoader = ClassLoader.getSystemClassLoader();
    final Set<String> excludes = new LinkedHashSet<>();
    for (final Class<?> cls : classes) {
      final String resourceName = cls.getName().replace('.', '/').concat(".class");
      final Enumeration<URL> urls = classLoader.getResources(resourceName);
      while (urls.hasMoreElements()) {
        final String path = urls.nextElement().getFile();
        excludes.add(path.startsWith("file:") ? path.substring(5, path.indexOf('!')) : path.substring(0, path.length() - resourceName.length() - 1));
      }
    }

    return excludes;
  }

  /**
   * @return A {@code Set} representation of paths in classpath of the current
   *         process.
   */
  private static Set<String> getJavaClassPath() {
    return new LinkedHashSet<>(Arrays.asList(System.getProperty("java.class.path").split(File.pathSeparator)));
  }

  /**
   * Returns a classpath string that includes the specified set of classpath
   * paths in the {@code includes} parameter, and excludes the specified set of
   * classpath paths from the {@code excludes} parameter.
   *
   * @param includes The paths to include in the returned classpath.
   * @param excludes The paths to exclude from the returned classpath.
   * @return A classpath string that includes paths from {@code includes}, and
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
   * Initializes the OpenTracing {@code Tracer} to be used for the duration of
   * this test process. If the {@code "-javaagent"} argument is not specified
   * for the current process, this function will return {@code null}.
   *
   * @return The OpenTracing {@code Tracer} to be used for the duration of this
   *         test process, or {@code null} if the {@code "-javaagent"} argument
   *         is not specified for the current process.
   */
  private static MockTracer initTracer() {
    final RuntimeMXBean runtimeMxBean = ManagementFactory.getRuntimeMXBean();
    final List<String> arguments = runtimeMxBean.getInputArguments();
    for (final String argument : arguments) {
      if (argument.startsWith("-javaagent")) {
        final MockTracer tracer = new MockTracer();
        if (logger.isLoggable(Level.FINEST)) {
          logger.finest("Registering tracer in forked InstrumentRunner: " + tracer);
          logger.finest("  Tracer ClassLoader: " + tracer.getClass().getClassLoader());
          logger.finest("  Tracer Location: " + ClassLoader.getSystemClassLoader().getResource(tracer.getClass().getName().replace('.', '/').concat(".class")));
          logger.finest("  GlobalTracer ClassLoader: " + GlobalTracer.class.getClassLoader());
          logger.finest("  GlobalTracer Location: " + ClassLoader.getSystemClassLoader().getResource(GlobalTracer.class.getName().replace('.', '/').concat(".class")));
        }

        GlobalTracer.register(tracer);
        System.out.println("sun.boot.class.path: " + System.getProperty("sun.boot.class.path"));
        return tracer;
      }
    }

    return null;
  }

  /**
   * Loads the specified class in an {@code URLClassLoader}. The class loader
   * will be initialized with the process classpath, and will be detached from
   * the System ClassLoader. This construct guarantees that any {@code cls}
   * passed to this function will be unable to resolve classes in the System
   * ClassLoader.
   * <p>
   * <i><b>Note:</b> If {@code cls} is present in the Bootstrap ClassLoader, it
   * will be resolved there instead of the {@code URLClassLoader}.</i>
   *
   * @param cls The {@code Class} to load in the {@code URLClassLoader}.
   * @return The class loaded in the {@code URLClassLoader}.
   * @throws InitializationError If the specified class cannot be located by the
   *           URLClassLoader.
   */
  private static Class<?> loadClassInURLClassLoader(final Class<?> cls) throws InitializationError {
    try {
      final String classpath = System.getProperty("java.class.path");
      if (logger.isLoggable(Level.FINEST))
        logger.finest("ClassPath of URLClassLoader:\n  " + classpath.replace(File.pathSeparator, "\n  "));

      final URL[] libs = Util.classPathToURLs(classpath);
      // Special case for AgentRunnerITest, because it belongs to the same classpath path as the AgentRunner
      final ClassLoader parent = System.getProperty("java.version").startsWith("1.") ? null : ClassLoader.getPlatformClassLoader();
      final URLClassLoader classLoader = new URLClassLoader(libs, cls != AgentRunnerITest.class ? parent : new ClassLoader() {
        @Override
        protected Class<?> loadClass(final String name, final boolean resolve) throws ClassNotFoundException {
          return name.equals(cls.getName()) ? null : super.loadClass(name, resolve);
        }
      });
      final Class<?> classInClassLoader = Class.forName(cls.getName(), false, classLoader);
      Assert.assertNotNull("Test class not resolvable in URLClassLoader: " + cls.getName(), classInClassLoader);
      Assert.assertNotNull("Test class must not be resolvable in BootClassLoader: " + cls.getName(), classInClassLoader.getClassLoader());
      Assert.assertEquals(URLClassLoader.class, classInClassLoader.getClassLoader().getClass());
      Assert.assertNull(Rule.class.getClassLoader());
      return classInClassLoader;
    }
    catch (final ClassNotFoundException e) {
      throw new InitializationError(e);
    }
  }

  private static final MockTracer tracer = initTracer();
  private static final boolean isInFork = tracer != null;

  private final ObjectInputStream in;
  private final ObjectOutputStream out;
  private final URL loggingConfigFile;

  /**
   * Creates a new {@code InstrumentationRunner} for the specified test class.
   *
   * @param cls The test class.
   * @throws InitializationError If the test class is malformed, or if the
   *           specified class cannot be located by the URLClassLoader in the
   *           forked process.
   */
  public AgentRunner(final Class<?> cls) throws InitializationError {
    super(isInFork ? loadClassInURLClassLoader(cls) : cls);
    final Debug debug = cls.getAnnotation(Debug.class);
    this.loggingConfigFile = debug != null && debug.value() ? getClass().getResource("/logging.properties") : null;
    if (loggingConfigFile != null) {
      try {
        LogManager.getLogManager().readConfiguration(loggingConfigFile.openStream());
      }
      catch (final IOException e) {
        throw new ExceptionInInitializerError(e);
      }
    }

    Thread shutdownHook = null;
    try {
      if (isInFork) {
        final int port = Integer.parseInt(System.getProperty(PORT_ARG));
        final Socket socket = new Socket("127.0.0.1", port);
        shutdownHook = new Thread() {
          @Override
          public void run() {
            try {
              socket.close();
            }
            catch (final IOException e) {
            }
          }
        };

        this.out = new ObjectOutputStream(socket.getOutputStream());
        this.in = null;
      }
      else {
        final ServerSocket serverSocket = new ServerSocket(0);
        final int port = serverSocket.getLocalPort();
        final Process process = fork(cls, port);
        shutdownHook = new Thread() {
          @Override
          public void run() {
            try {
              serverSocket.close();
            }
            catch (final IOException e) {
            }

            if (!process.isAlive())
              return;

            if (logger.isLoggable(Level.FINEST))
              logger.finest("Destroying forked process...");

            process.destroy();
          }
        };

        final AtomicBoolean initialized = new AtomicBoolean(false);
        new Thread() {
          @Override
          public void run() {
            try {
              process.waitFor();
            }
            catch (final InterruptedException e) {
            }

            if (initialized.get())
              return;

            try {
              serverSocket.close();
            }
            catch (final IOException e) {
            }
          }
        }.start();

        final Socket socket = serverSocket.accept();
        this.out = null;
        this.in = new ObjectInputStream(socket.getInputStream());
        initialized.set(true);
      }
    }
    catch (final IOException e) {
      throw new InitializationError(e);
    }
    finally {
      if (shutdownHook != null)
        Runtime.getRuntime().addShutdownHook(shutdownHook);
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
        final List<FrameworkMethod> augmented = new ArrayList<>();
        for (final FrameworkMethod method : super.getAnnotatedMethods(annotationClass))
          augmented.add(retrofitMethod(method));

        return Collections.unmodifiableList(augmented);
      }

      @Override
      protected void scanAnnotatedMembers(final Map<Class<? extends Annotation>,List<FrameworkMethod>> methodsForAnnotations, final Map<Class<? extends Annotation>,List<FrameworkField>> fieldsForAnnotations) {
        super.scanAnnotatedMembers(methodsForAnnotations, fieldsForAnnotations);
        for (final List<FrameworkMethod> methods : methodsForAnnotations.values()) {
          final ListIterator<FrameworkMethod> iterator = methods.listIterator();
          while (iterator.hasNext())
            iterator.set(retrofitMethod(iterator.next()));
        }
      }
    };
  }

  /**
   * Retrofits the specified {@code FrameworkMethod} to work with the forked
   * testing design of this runner.
   *
   * @param method The {@code FrameworkMethod} to retrofit.
   * @return The retrofitted {@code FrameworkMethod}.
   */
  private FrameworkMethod retrofitMethod(final FrameworkMethod method) {
    return new FrameworkMethod(method.getMethod()) {
      @Override
      public void validatePublicVoidNoArg(final boolean isStatic, final List<Throwable> errors) {
        validatePublicVoid(isStatic, errors);
        if (method.getMethod().getParameterTypes().length != 1)
          errors.add(new Exception("Method " + method.getName() + " must declare one parameter of type: " + MockTracer.class.getName()));
      }

      @Override
      public Object invokeExplosively(final Object target, final Object ... params) throws Throwable {
        if (logger.isLoggable(Level.FINEST))
          logger.finest("invokeExplosively [" + getName() + "](" + target + ")");

        if (isInFork) {
          final ClassLoader classLoader = isStatic() ? method.getDeclaringClass().getClassLoader() : target.getClass().getClassLoader();
          Assert.assertEquals("Method " + getName() + " should be executed in URLClassLoader", URLClassLoader.class, classLoader == null ? null : classLoader.getClass());
          try {
            tracer.reset();
            final Object result = super.invokeExplosively(target, tracer);
            write(new TestResult(getName(), null));
            return result;
          }
          catch (final Throwable t) {
            if (logger.isLoggable(Level.FINEST))
              logger.finest("Throwing: " + t.getClass().getName());

            write(new TestResult(getName(), t));
            throw t;
          }
        }

        final TestResult testResult = read();
        // Test execution order is guaranteed to be deterministic
        // https://github.com/junit-team/junit4/wiki/test-execution-order#test-execution-order
        Assert.assertEquals(getName(), testResult.getMethodName());
        if (testResult.getTargetException() != null)
          throw testResult.getTargetException();

        return new ReflectiveCallable() {
          @Override
          protected Object runReflectiveCall() throws Throwable {
            return null;
          }
        }.run();
      }
    };
  }

  /**
   * @return The classpath path of the opentracing-specialagent.
   */
  protected String getAgentPath() {
    return Agent.class.getProtectionDomain().getCodeSource().getLocation().getFile();
  }

  /**
   * Fork the javaagent process for the specified JUnit test class, and socket port.
   *
   * @param mainClass The main class to invoke with JUnit.
   * @param port The socket port on which the parent process is waiting for connection.
   * @return The {@code Process} reference of the forked process.
   * @throws IOException If an I/O error has occurred.
   */
  private Process fork(final Class<?> mainClass, final int port) throws IOException {
    if (isInFork)
      throw new IllegalStateException("Attempting to fork from a fork");

    // Use the whole java.class.path for the forked process, because any class
    // on the classpath may be used in the implementation of the test method.
    final String classpath = buildClassPath(getJavaClassPath(), null);
    if (logger.isLoggable(Level.FINEST))
      logger.finest("ClassPath of forked process will be:\n  " + classpath.replace(File.pathSeparator, "\n  "));

    // It is necessary to add the classpath locations of Tracer, NoopTracer,
    // GlobalTracer, TracerResolver, Agent, and AgentRunner to the
    // BootClassLoader, because:
    // 1) These classes are required for the test to run, since the test
    //    directly references these classes, leaving just the SystemClassLoader
    //    and BootClassLoader as the only options.
    // 2) The URLClassLoader must be detached from the SystemClassLoader, thus
    //    requiring the AgentRunner class to be loaded in the BootClassLoader,
    //    or otherwise Byteman will load the AgentRunner$MockTracer in the
    //    BootClassLoader, while this code will load AgentRunner$MockTracer
    //    in URLClassLoader.
    final String bootClassPath = buildClassPath(getLocations(Tracer.class, NoopTracer.class, GlobalTracer.class, TracerResolver.class, Agent.class, AgentRunner.class), null);
    if (logger.isLoggable(Level.FINEST))
      logger.finest("BootClassPath of forked process will be:\n  " + bootClassPath.replace(File.pathSeparator, "\n  "));

    int i = -1;
    final String[] args = new String[8 + (loggingConfigFile != null ? 1 : 0)];
    args[++i] = "java";
    args[++i] = "-Xbootclasspath/a:" + bootClassPath;
    args[++i] = "-cp";
    args[++i] = classpath;
    args[++i] = "-javaagent:" + getAgentPath();
    args[++i] = "-D" + PORT_ARG + "=" + port;
    if (loggingConfigFile != null)
      args[++i] = "-Djava.util.logging.config.file=" + ("file".equals(loggingConfigFile.getProtocol()) ? loggingConfigFile.getPath() : loggingConfigFile.toString());

    args[++i] = JUnitCore.class.getName();
    args[++i] = mainClass.getName();

    if (logger.isLoggable(Level.FINEST))
      logger.finest("Forking process:\n  " + Arrays.toString(args).replace(", ", " "));

    final ProcessBuilder builder = new ProcessBuilder(args);
    builder.inheritIO();
    return builder.start();
  }

  /**
   * Returns a {@code TestResult} object serialized over the socket. This method
   * will block until an object is available.
   *
   * @return A {@code TestResult} object serialized over the socket.
   */
  private TestResult read() {
    try {
      final TestResult result = (TestResult)in.readObject();
      if (logger.isLoggable(Level.FINEST))
        logger.finest("Read: " + result);

      return result;
    }
    catch (final ClassNotFoundException | IOException e) {
      throw new IllegalStateException(e);
    }
  }

  /**
   * Writes a {@code TestResult} object to the socket.
   *
   * @param result The {@code TestResult} object to write.
   */
  private void write(final TestResult result) {
    try {
      if (logger.isLoggable(Level.FINEST))
        logger.finest("Write: " + result);

      out.writeObject(result);
    }
    catch (final IOException e) {
      throw new IllegalStateException(e);
    }
  }
}