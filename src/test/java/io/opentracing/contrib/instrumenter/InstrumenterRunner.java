package io.opentracing.contrib.instrumenter;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.jboss.byteman.rule.Rule;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.Description;
import org.junit.runner.JUnitCore;
import org.junit.runner.Runner;
import org.junit.runner.notification.RunNotifier;

import io.opentracing.Tracer;
import io.opentracing.contrib.tracerresolver.TracerResolver;
import io.opentracing.mock.MockTracer;
import io.opentracing.noop.NoopTracer;
import io.opentracing.util.GlobalTracer;

public class InstrumenterRunner extends Runner {
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

  public static void fork(final Class<?> mainClass) throws InterruptedException, IOException {
    // Use the whole java.class.path for the forked process, because any class
    // on the classpath may be used in the implementation of the test method.
    final String classpath = buildClassPath(getJavaClassPath(), null);

    System.err.println("ClassPath of forked process:\n" + classpath.replace(':', '\n'));

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
    final String bootClassPath = buildClassPath(getLocations(Tracer.class, NoopTracer.class, GlobalTracer.class, TracerResolver.class, OpenTracingAgent.class, InstrumenterTest.class), null);

    // The agent jar
    System.out.println(getLocations(OpenTracingAgent.class));
    final String instrumenterPath = OpenTracingAgent.class.getProtectionDomain().getCodeSource().getLocation().getFile();

    final String[] args = {"java", "-cp", classpath, "-Xbootclasspath/a:" + bootClassPath, "-javaagent:" + instrumenterPath, JUnitCore.class.getName(), mainClass.getName()};
    System.err.println(Arrays.toString(args));
    final ProcessBuilder builder = new ProcessBuilder(args);
    builder.inheritIO();

    final Process process = builder.start();
    Runtime.getRuntime().addShutdownHook(new Thread() {
      @Override
      public void run() {
        process.destroyForcibly();
      }
    });
    process.waitFor();
  }

  static {
    final String classpath = System.getProperty("java.class.path");
    System.err.println("java.class.path in InstrumenterRunner\n" + classpath.replace(':', '\n'));
  }

  private static final MockTracer tracer = new MockTracer();

  static {
    System.err.println("Registering tracer in InstrumentRunner: " + tracer);
    System.err.println("  ClassLoader: " + tracer.getClass().getClassLoader() + " " + ClassLoader.getSystemClassLoader().getResource(tracer.getClass().getName().replace('.', '/').concat(".class")));
    System.err.println("  GlobalTracer ClassLoader: " + GlobalTracer.class.getClassLoader() + " " + ClassLoader.getSystemClassLoader().getResource(GlobalTracer.class.getName().replace('.', '/').concat(".class")));
    GlobalTracer.register(tracer);
  }

  private Class<?> testClass;

  public InstrumenterRunner(final Class<?> testClass) {
    this.testClass = testClass;
  }

  @Override
  public Description getDescription() {
    return Description.createTestDescription(testClass, "OpenTracing Instrumenter Test Runner");
  }

  @Override
  public void run(final RunNotifier notifier) {
    final RuntimeMXBean runtimeMxBean = ManagementFactory.getRuntimeMXBean();
    final List<String> arguments = runtimeMxBean.getInputArguments();
    System.out.println(arguments);
    boolean isInFork = false;
    for (final String argument : arguments)
      if (isInFork = argument.startsWith("-javaagent"))
        break;

    try {
      if (isInFork) {
        final String classpath = System.getProperty("java.class.path");
        System.err.println("ClassPath of URLClassLoader:\n" + classpath.replace(':', '\n'));
        final URL[] libs = buildClassPath(classpath);
        try (final URLClassLoader classLoader = new URLClassLoader(libs, null)) {
          final Class<?> classInClassLoader = Class.forName(testClass.getName(), false, classLoader);
          Assert.assertEquals(URLClassLoader.class, classInClassLoader.getClassLoader().getClass());
          Assert.assertNull(Rule.class.getClassLoader());
          invoke(classInClassLoader, notifier, isInFork);
        }
      }
      else {
        // Validate the methods in the test class to ensure they have correct parameters
        invoke(testClass, notifier, false);
      }

      // Fork the test with -javaagent
      if (!isInFork)
        fork(testClass);
    }
    catch (final ClassNotFoundException | InstantiationException | IllegalAccessException | InterruptedException | InvocationTargetException | IOException | NoSuchMethodException e) {
      throw new IllegalStateException(e);
    }
  }

  public static void invoke(final Class<?> testClass, final RunNotifier notifier, final boolean invoke) throws IllegalAccessException, InstantiationException, InvocationTargetException, NoSuchMethodException {
    System.err.println("InstrumenterRunner.invoke(" + testClass.getName() + ", " + invoke + "): ClassLoader: " + testClass.getClassLoader() + ", Location: " + ClassLoader.getSystemClassLoader().getResource(testClass.getName().replace('.', '/').concat(".class")));
    final Object testObject = invoke ? testClass.getConstructor().newInstance() : null;
    for (final Method method : testClass.getMethods()) {
      if (method.isAnnotationPresent(Test.class)) {
        if (invoke)
          notifier.fireTestStarted(Description.createTestDescription(testClass, method.getName()));

        if (method.getParameterTypes().length != 1 || MockTracer.class != method.getParameterTypes()[0])
          throw new IllegalStateException("Methods annotated with @Test must specify one parameter of type: " + MockTracer.class.getName());

        if (invoke) {
          method.invoke(testObject, tracer);
          notifier.fireTestFinished(Description.createTestDescription(testClass, method.getName()));
        }
      }
    }
  }
}