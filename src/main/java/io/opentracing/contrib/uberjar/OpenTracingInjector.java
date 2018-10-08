package io.opentracing.contrib.uberjar;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLClassLoader;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

public class OpenTracingInjector {
  private static final int DEFAULT_SOCKET_BUFFER_SIZE = 65536;
  private static final Map<ClassLoader,Set<String>> classLoaderToClassName = new IdentityHashMap<>();
  public static final Map<ClassLoader,URLClassLoader> classLoaderToPluginClassLoader = new IdentityHashMap<>();

  public static byte[] findClass(final ClassLoader classLoader, final String name) throws IOException {
    // Check if the classLoader matches a pluginClassLoader
    final URLClassLoader pluginClassLoader = classLoaderToPluginClassLoader.get(classLoader);
    System.out.println("Checking if ClassLoader matches target: " + (pluginClassLoader != null));
    if (pluginClassLoader == null)
      return null;

    // Check that the resourceName has not already been retrieved by this method (this may
    // be a moot point, because the JVM won't call findClass() twice for the same class
    final String resourceName = name.replace('.', '/').concat(".class");
    Set<String> classNames = classLoaderToClassName.get(classLoader);
    if (classNames == null)
      classLoaderToClassName.put(classLoader, classNames = new HashSet<>());
    else if (classNames.contains(resourceName))
      return null;

    classNames.add(resourceName);

    // Return the resource's bytes, or null if the resource does not exist in pluginClassLoader
    final InputStream in = pluginClassLoader.getResourceAsStream(resourceName);
    return in == null ? null : readBytes(in);
  }

  private static byte[] readBytes(final InputStream in) throws IOException {
    final ByteArrayOutputStream buffer = new ByteArrayOutputStream(DEFAULT_SOCKET_BUFFER_SIZE);
    final byte[] data = new byte[DEFAULT_SOCKET_BUFFER_SIZE];
    for (int len; (len = in.read(data)) != -1; buffer.write(data, 0, len));
    return buffer.toByteArray();
  }
}