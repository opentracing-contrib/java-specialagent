package io.opentracing.uberjar;

import java.io.File;
import java.io.IOException;
import java.net.JarURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.ServiceLoader;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import io.opentracing.contrib.agent.api.Discovery;

public class Main {
  private static List<URL> findJarPath(final String path) throws IOException {
    final Path to = Files.createTempDirectory("opentracing");
    to.toFile().deleteOnExit();
    final URL url = Thread.currentThread().getContextClassLoader().getResource(path);
    if (url == null)
      return null;

    final JarURLConnection jarURLConnection = (JarURLConnection)url.openConnection();
    jarURLConnection.setUseCaches(false);
    final JarFile jarFile = jarURLConnection.getJarFile();

    final List<URL> resources = new ArrayList<>();
    final Enumeration<JarEntry> enumeration = jarFile.entries();
    while (enumeration.hasMoreElements()) {
      final String entry = enumeration.nextElement().getName();
      if (entry.length() > path.length() && entry.startsWith(path)) {
        final int slash = entry.lastIndexOf('/');
        final File dir = new File(to.toFile(), entry.substring(0, slash));
        dir.mkdirs();
        dir.deleteOnExit();
        final File file = new File(dir, entry.substring(slash + 1));
        file.deleteOnExit();
        final URL u = new URL(url, entry.substring(path.length()));
        Files.copy(u.openStream(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        resources.add(file.toURI().toURL());
      }
    }

    return resources;
  }

  private static void discover(final Discovery discovery, final URLClassLoader uberClassLoader) throws MalformedURLException {
    final String module = discovery.exists();
    if (module != null) {
      final URL jarUrl = uberClassLoader.findResource("META-INF/maven/" + module.replace(':', '/') + "/pom.properties");
      final URL rulesUrl = new URL(discovery.getClass().getProtectionDomain().getCodeSource().getLocation(), "otarules.btm");
      loadModule(new URL(jarUrl.toString().substring(4, jarUrl.toString().indexOf('!'))), rulesUrl);
    }
  }

  private static void loadModule(final URL jarUrl, final URL rulesUrl) {
    System.out.println(jarUrl);
    System.out.println(rulesUrl);
  }

  public static void main(final String[] args) throws IOException {
    final List<URL> resources = findJarPath("META-INF/opentracing/");
    final URLClassLoader uberClassLoader = new URLClassLoader(resources.toArray(new URL[resources.size()]));
    final ServiceLoader<Discovery> loader = ServiceLoader.load(Discovery.class, uberClassLoader);
    final Iterator<Discovery> iterator = loader.iterator();
    while (iterator.hasNext()) {
      discover(iterator.next(), uberClassLoader);
    }
  }
}