package io.opentracing.contrib.instrumenter;

import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.annotation.Annotation;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.internal.AssumptionViolatedException;
import org.junit.internal.runners.model.EachTestNotifier;
import org.junit.internal.runners.model.ReflectiveCallable;
import org.junit.runner.Description;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runner.notification.RunListener;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.FrameworkField;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.Statement;
import org.junit.runners.model.TestClass;

import io.opentracing.Tracer;
import io.opentracing.contrib.tracerresolver.TracerResolver;
import io.opentracing.mock.MockTracer;
import io.opentracing.noop.NoopTracer;
import io.opentracing.util.GlobalTracer;

public class InstrumenterRunner extends BlockJUnit4ClassRunner {
  private static final String PORT_ARG = "io.opentracing.contrib.instrumenter.port";

  static {
    final String classpath = System.getProperty("java.class.path");
    System.out.println("java.class.path in InstrumenterRunner\n" + classpath.replace(':', '\n'));
  }

  private static Set<String> getLocations(final Class<?> ... classes) throws IOException {
    final ClassLoader classLoader = ClassLoader.getSystemClassLoader();
    final Set<String> excludes = new HashSet<>();
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

  private static Set<String> getJavaClassPath() {
    return new LinkedHashSet<>(Arrays.asList(System.getProperty("java.class.path").split(":")));
  }

  private static String buildClassPath(final Set<String> includes, final Set<String> excludes) {
    if (excludes != null)
      includes.removeAll(excludes);

    final StringBuilder builder = new StringBuilder();
    final Iterator<String> iterator = includes.iterator();
    for (int i = 0; iterator.hasNext(); ++i) {
      if (i > 0)
        builder.append(':');

      builder.append(iterator.next());
    }

    return builder.toString();
  }

  private static URL[] buildClassPath(final String classpath) throws MalformedURLException {
    final String[] paths = classpath.split(":");
    final URL[] libs = new URL[paths.length];
    for (int i = 0; i < paths.length; ++i)
      libs[i] = new File(paths[i]).toURI().toURL();

    return libs;
  }

  private static MockTracer init() {
    final RuntimeMXBean runtimeMxBean = ManagementFactory.getRuntimeMXBean();
    final List<String> arguments = runtimeMxBean.getInputArguments();
    for (final String argument : arguments) {
      if (argument.startsWith("-javaagent")) {
        final MockTracer tracer = new MockTracer();
        System.err.println("Registering tracer in forked InstrumentRunner: " + tracer);
        System.err.println("  ClassLoader: " + tracer.getClass().getClassLoader() + " " + ClassLoader.getSystemClassLoader().getResource(tracer.getClass().getName().replace('.', '/').concat(".class")));
        System.err.println("  GlobalTracer ClassLoader: " + GlobalTracer.class.getClassLoader() + " " + ClassLoader.getSystemClassLoader().getResource(GlobalTracer.class.getName().replace('.', '/').concat(".class")));
        GlobalTracer.register(tracer);
        return tracer;
      }
    }

    return null;
  }

  private static final MockTracer tracer = init();
  private static final boolean isInFork = tracer != null;

  private static Class<?> getTargetClass(final Class<?> cls) throws InitializationError {
    if (!isInFork)
      return cls;

    try {
      final String classpath = System.getProperty("java.class.path");
      System.err.println("ClassPath of URLClassLoader:\n" + classpath.replace(':', '\n'));
      final URL[] libs = buildClassPath(classpath);
      final URLClassLoader classLoader = new URLClassLoader(libs, new ClassLoader() {
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
    catch (final ClassNotFoundException | MalformedURLException e) {
      throw new InitializationError(e);
    }
  }

  private final ObjectInputStream in;
  private final ObjectOutputStream out;
  private Throwable nextThrowable;

  public InstrumenterRunner(final Class<?> cls) throws InitializationError {
    super(getTargetClass(cls));
    try {
      if (isInFork) {
        final int port = Integer.parseInt(System.getProperty(PORT_ARG));
        final Socket socket = new Socket("127.0.0.1", port);
        this.out = new ObjectOutputStream(socket.getOutputStream());
        this.in = new ObjectInputStream(socket.getInputStream());
      }
      else {
        final ServerSocket serverSocket = new ServerSocket(0);
        final int port = serverSocket.getLocalPort();
        final Process process = fork(cls, port);
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
        this.out = new ObjectOutputStream(socket.getOutputStream());
        this.in = new ObjectInputStream(socket.getInputStream());
        initialized.set(true);
      }
    }
    catch (final IOException e) {
      throw new InitializationError(e);
    }
  }

  private static FrameworkMethod getMethod(final TestClass testClass, final Request request) {
    for (final FrameworkMethod method : testClass.getAnnotatedMethods())
      if (request.getMethodName().equals(method.getName()) && Arrays.equals(request.getParameterTypes(), method.getMethod().getParameterTypes()))
        return method;

    return null;
  }

  @Override
  protected Object createTest() throws Exception {
    return isInFork ? getTestClass().getOnlyConstructor().newInstance() : super.createTest();
  }

  private FrameworkMethod tweakMethod(final FrameworkMethod method) {
    return new FrameworkMethod(method.getMethod()) {
      @Override
      public void validatePublicVoidNoArg(boolean isStatic, List<Throwable> errors) {
        validatePublicVoid(isStatic, errors);
        if (method.getMethod().getParameterTypes().length != 1) {
          errors.add(new Exception("Method " + method.getName() + " must declare one parameter of type " + MockTracer.class.getName()));
        }
      }

      @Override
      public Object invokeExplosively(final Object target, final Object ... params) throws Throwable {
        log("invokeExplosively [" + getName() + ", " + isStatic() + "](" + target + ")");
        if (isInFork) {
          final ClassLoader classLoader = isStatic() ? method.getDeclaringClass().getClassLoader() : target.getClass().getClassLoader();
          Assert.assertNotNull("Method getName() should not be executed in BootClassLoader", classLoader);
          Assert.assertEquals("Method getName() should be executed in URLClassLoader", URLClassLoader.class, classLoader.getClass());
          try {
            final Object obj = super.invokeExplosively(target, tracer);
            if (getAnnotation(AfterClass.class) != null)
              write(new Response(getName(), null, true, null));

            return obj;
          }
          catch (final Throwable t) {
            if (getAnnotation(AfterClass.class) == null)
              throw t;

            log("t: " + t.getClass().getName());
            write(new Response(getName(), null, true, t));
          }
        }

        if (!isStatic())
          throw new IllegalStateException("Should not get here");

        if (getAnnotation(AfterClass.class) != null) {
          write(new Request(getMethod().getName(), method.getMethod().getParameterTypes()));
          final Response response = (Response)read();
          if (response != null && response.getTargetException() != null)
            throw response.getTargetException();
        }

        return new ReflectiveCallable() {
          @Override
          protected Object runReflectiveCall() throws Throwable {
            return null;
          }
        }.run();
      }
    };
  }

  @Override
  protected TestClass createTestClass(final Class<?> testClass) {
    return new TestClass(testClass) {
      @Override
      public List<FrameworkMethod> getAnnotatedMethods(final Class<? extends Annotation> annotationClass) {
        final List<FrameworkMethod> augmented = new ArrayList<>();
        for (final FrameworkMethod method : super.getAnnotatedMethods(annotationClass))
          augmented.add(tweakMethod(method));

        return Collections.unmodifiableList(augmented);
      }

      @Override
      protected void scanAnnotatedMembers(final Map<Class<? extends Annotation>,List<FrameworkMethod>> methodsForAnnotations, final Map<Class<? extends Annotation>,List<FrameworkField>> fieldsForAnnotations) {
        super.scanAnnotatedMembers(methodsForAnnotations, fieldsForAnnotations);
        for (final List<FrameworkMethod> methods : methodsForAnnotations.values()) {
          final ListIterator<FrameworkMethod> iterator = methods.listIterator();
          while (iterator.hasNext())
            iterator.set(tweakMethod(iterator.next()));
        }
      }
    };
  }

  private static void log(final String message) {
    log(message);
  }

  @Override
  public void run(final RunNotifier notifier) {
    log("run(" + notifier + ")");
    if (!isInFork) {
      notifier.addListener(new RunListener() {
        private Class<?> testClass;

        @Override
        public void testFinished(final Description description) throws Exception {
          this.testClass = description.getTestClass();
        }

        @Override
        public void testRunFinished(final Result result) throws Exception {
          if (testClass != null)
            write(null);
        }
      });
    }

    super.run(notifier);
  }

  @Override
  protected void runChild(FrameworkMethod method, final RunNotifier notifier) {
    log("runChild(" + method + ", " + notifier + ")");
    if (isInFork) {
      for (Request request; (request = (Request)read()) != null;) {
        method = getMethod(getTestClass(), request);
        if (!isIgnored(method)) {
          final ForkTestNotifier eachNotifier = new ForkTestNotifier(notifier, describeChild(method), out);
          if (method.isStatic()) {
            try {
              method.invokeExplosively(null, tracer);
            }
            catch (final Throwable t) {
              eachNotifier.addFailure(t);
            }
          }
          else if (!isIgnored(method)) {
            eachNotifier.fireTestStarted();
            try {
              methodBlock(method).evaluate();
            }
            catch (final AssumptionViolatedException e) {
              eachNotifier.addFailedAssumption(e);
            }
            catch (final Throwable e) {
              eachNotifier.addFailure(e);
            }
            finally {
              eachNotifier.fireTestFinished();
            }
          }
        }
      }
    }
    else if (isIgnored(method)) {
      notifier.fireTestIgnored(describeChild(method));
    }
    else if (nextThrowable == null) {
      final EachTestNotifier eachNotifier = new EachTestNotifier(notifier, describeChild(method));
      write(new Request(method.getMethod().getName(), method.getMethod().getParameterTypes()));
      try {
        for (Response response; (response = (Response)read()) != null;) {
          for (final Method m : EachTestNotifier.class.getMethods()) {
            if (response.getNotifierMethod() == null) {
              nextThrowable = response.getTargetException();
            }
            else if (m.getName().equals(response.getNotifierMethod())) {
              if (m.getParameterTypes().length == 1) {
                m.invoke(eachNotifier, response.getTargetException());
              }
              else {
                m.invoke(eachNotifier);
              }

              break;
            }
          }

          if (response.isTerminal())
            break;
        }
      }
      catch (final IllegalAccessException | InvocationTargetException e) {
        throw new UnsupportedOperationException(e);
      }
    }
  }

  @Override
  protected Statement childrenInvoker(final RunNotifier notifier) {
    return new Statement() {
      @Override
      public void evaluate() throws Throwable {
        InstrumenterRunner.super.childrenInvoker(notifier).evaluate();
        if (nextThrowable != null)
          throw nextThrowable;
      }
    };
  }

  @Override
  protected Statement methodInvoker(final FrameworkMethod method, final Object test) {
    log("methodInvoker(" + method + ", " + test + ")");
    return new Statement() {
      @Override
      public void evaluate() throws Throwable {
        log("evaluate: " + method + ", " + test);
        if (isIgnored(method))
          return;

        method.invokeExplosively(test);
      }
    };
  }

  protected String getInstrumenterPath() {
    return OpenTracingAgent.class.getProtectionDomain().getCodeSource().getLocation().getFile();
  }

  private Process fork(final Class<?> mainClass, final int port) throws IOException {
    if (isInFork)
      throw new IllegalStateException("Attempting to fork from a fork");

    // Use the whole java.class.path for the forked process, because any class
    // on the classpath may be used in the implementation of the test method.
    final String classpath = buildClassPath(getJavaClassPath(), null);

    System.out.println("ClassPath of forked process:\n" + classpath.replace(':', '\n'));

    // It is necessary to add the classpath locations of Tracer, NoopTracer,
    // GlobalTracer, TracerResolver, OpenTracingAgent and InstrumenterTest
    // to the BootClassLoader, because:
    // 1) These classes are required for the test to run, since the test
    //    directly references these classes, leaving just the SystemClassLoader
    //    and BootClassLoader as the only options.
    // 2) The URLClassLoader must be detached from the SystemClassLoader, thus
    //    requiring the InstrumenterTest class to be loaded in the
    //    BootClassLoader, or otherwise Byteman will load the
    //    InstrumenterTest$MockTracer in the BootClassLoader, while this code
    //    will load InstrumenterTest$MockTracer in URLClassLoader.
    final String bootClassPath = buildClassPath(getLocations(Tracer.class, NoopTracer.class, GlobalTracer.class, TracerResolver.class, OpenTracingAgent.class, InstrumenterRunner.class), null);

    // The agent jar
    System.out.println(getLocations(OpenTracingAgent.class));

    final String[] args = {"java", "-Xbootclasspath/a:" + bootClassPath, "-cp", classpath, "-javaagent:" + getInstrumenterPath(), "-D" + PORT_ARG + "=" + port, JUnitCore.class.getName(), mainClass.getName()};
    System.out.println(Arrays.toString(args).replace(", ", " "));
    final ProcessBuilder builder = new ProcessBuilder(args);
    builder.inheritIO();

    final Process process = builder.start();

    Runtime.getRuntime().addShutdownHook(new Thread() {
      @Override
      public void run() {
        process.destroyForcibly();
      }
    });

    return process;
  }

  private Object read() {
    try {
      final Object obj = in.readObject();
      log("Read: " + obj);
      return obj;
    }
    catch (final ClassNotFoundException | IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private void write(final Object obj) {
    try {
      log("Write: " + obj);
      out.writeObject(obj);
    }
    catch (final IOException e) {
      throw new IllegalStateException(e);
    }
  }
}