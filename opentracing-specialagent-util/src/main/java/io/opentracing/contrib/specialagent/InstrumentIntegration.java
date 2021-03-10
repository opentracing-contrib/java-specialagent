package io.opentracing.contrib.specialagent;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InstrumentIntegration {
  private static String GLOBAL_CLASSPATH_OPT = "sa.integration.classpath";

  private static final Logger logger = Logger.getLogger(InstrumentIntegration.class);

  private static InstrumentIntegration SINGLETON;

  private URLClassLoader GLOBAL;

  private Map<String, URLClassLoader> extensionClassLoaders = new HashMap<>();

  public static void init(final Map<String, String[]> extensionClasspaths) {
    if (SINGLETON != null) {
      SINGLETON.clear();
    }
    SINGLETON = new InstrumentIntegration(extensionClasspaths);
  }

  public static InstrumentIntegration getInstance() {
    if (SINGLETON == null) {
      init(null);
    }
    return SINGLETON;
  }

  private InstrumentIntegration(final Map<String, String[]> extensionClasspaths) {
    if (extensionClasspaths != null) {
      final String[] globalClasspaths = extensionClasspaths.get(GLOBAL_CLASSPATH_OPT);
      if (GLOBAL == null && globalClasspaths != null) {
        GLOBAL = this.createClassLoader(globalClasspaths, null);
      }

      for (final Map.Entry<String, String[]> entry : extensionClasspaths.entrySet()) {
        extensionClassLoaders.put(entry.getKey(), this.createClassLoader(entry.getValue(), GLOBAL));
      }
    }
  }

  private URLClassLoader createClassLoader(final String[] classpaths, ClassLoader parent) {
    final URL[] urls = new URL[classpaths.length];
    for (int i = 0; i < classpaths.length; ++i) {
      final String part = classpaths[i];
      try {
        urls[i] = new URL("file", "", part.endsWith(".jar") || part.endsWith("/") ? part : part + "/");
      } catch (final MalformedURLException e) {
        logger.log(Level.WARNING, part + "is not a valid URL");
      }
    }
    return new URLClassLoader(urls, parent);
  }

  public <T> List<T> getExtensionInstances(Class<T> clazz, String extensionClasspathOpt, String... classNames) {
    final ClassLoader classLoader = this.extensionClassLoaders.get(extensionClasspathOpt);
    final List<T> result = new ArrayList<>();
    if (classNames != null) {
      for (final String className : classNames) {
        final T object = newInstance(classLoader, clazz, className);
        if (object != null)
          result.add(object);
      }
    }
    return result;
  }

  private <T> T newInstance(final ClassLoader classLoader, final Class<T> clazz, final String className) {
    try {
      Class<?> decoratorClass = loadClass(classLoader, className);
      if (decoratorClass == null)
        decoratorClass = loadClass(clazz.getClassLoader(), className);

      if (decoratorClass == null)
        return null;

      if (clazz.isAssignableFrom(decoratorClass))
        return clazz.cast(decoratorClass.newInstance());

      logger.log(Level.WARNING, className + " is not a subclass of " + clazz.getName());
    } catch (final InstantiationException | IllegalAccessException e) {
      logger.log(Level.SEVERE, e.getMessage(), e);
    }

    return null;
  }

  private Class<?> loadClass(ClassLoader classLoader, String className) {
    if (classLoader == null)
      return null;

    try {
      return classLoader.loadClass(className);
    } catch (final ClassNotFoundException e) {
      logger.log(Level.SEVERE, e.getMessage(), e);
    }

    return null;
  }

  private void clear() {
    if (this.GLOBAL != null) {
      try {
        this.GLOBAL.close();
      } catch (IOException e) {
        logger.finer("Closing classLoader fail: " + e.getMessage());
      }
      this.GLOBAL = null;
    }
    for (URLClassLoader classLoader : this.extensionClassLoaders.values()) {
      try {
        classLoader.close();
      } catch (IOException e) {
        logger.finer("Closing classLoader fail: " + e.getMessage());
      }
    }
    this.extensionClassLoaders.clear();
  }
}
