/*
 * Copyright 2017 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.opentracing.contrib.uberjar;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public final class OpenTracingUtil {
  private static final int DEFAULT_SOCKET_BUFFER_SIZE = 65536;

  /**
   * Returns the array of bytes read from an {@code InputStream}.
   *
   * @param in The {@code InputStream}.
   * @return The array of bytes read from an {@code InputStream}.
   * @throws IOException If an I/O error has occurred.
   */
  public static byte[] readBytes(final InputStream in) throws IOException {
    final ByteArrayOutputStream buffer = new ByteArrayOutputStream(DEFAULT_SOCKET_BUFFER_SIZE);
    final byte[] data = new byte[DEFAULT_SOCKET_BUFFER_SIZE];
    for (int len; (len = in.read(data)) != -1; buffer.write(data, 0, len));
    return buffer.toByteArray();
  }

  /**
   * Returns the hexadecimal representation of an object's identity hash code.
   *
   * @param obj The object.
   * @return The hexadecimal representation of an object's identity hash code.
   */
  public static String getIdentityCode(final Object obj) {
    return obj.getClass().getName() + "@" + Integer.toString(System.identityHashCode(obj), 16);
  }

  /**
   * Returns a {@code List} of {@code URL} objects having a prefix path that
   * matches {@code path}. This method will add a shutdown hook to delete any
   * temporary directory and file resources it created.
   *
   * @param path The prefix path to match when finding resources.
   * @return A {@code List} of {@code URL} objects having a prefix path that
   *         matches {@code path}.
   * @throws IOException If an I/O error has occurred.
   */
  public static List<URL> findResources(final String path) throws IOException {
    final URL resource = ClassLoader.getSystemClassLoader().getResource(path);
    if (resource == null)
      return null;

    final JarURLConnection jarURLConnection = (JarURLConnection)resource.openConnection();
    jarURLConnection.setUseCaches(false);
    final JarFile jarFile = jarURLConnection.getJarFile();

    final Path destPath = Files.createTempDirectory("opentracing");
    final File destDir = destPath.toFile();

    final List<URL> resources = new ArrayList<>();
    final Enumeration<JarEntry> enumeration = jarFile.entries();
    while (enumeration.hasMoreElements()) {
      final String entry = enumeration.nextElement().getName();
      if (entry.length() > path.length() && entry.startsWith(path)) {
        final int slash = entry.lastIndexOf('/');
        final File subDir = new File(destDir, entry.substring(0, slash));
        subDir.mkdirs();
        final File file = new File(subDir, entry.substring(slash + 1));
        final URL url = new URL(resource, entry.substring(path.length()));
        Files.copy(url.openStream(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        resources.add(file.toURI().toURL());
      }
    }

    Runtime.getRuntime().addShutdownHook(new Thread() {
      @Override
      public void run() {
        deleteDir(destDir);
      }
    });

    return resources;
  }

  /**
   * Recursively delete a directory and its contents.
   *
   * @param dir The directory to delete.
   * @return {@code true} if {@code dir} was successfully deleted, {@code false}
   *         otherwise.
   */
  private static boolean deleteDir(final File dir) {
    final File[] files = dir.listFiles();
    if (files != null)
      for (final File file : files)
        deleteDir(file);

    return dir.delete();
  }

  private OpenTracingUtil() {
  }
}