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

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * An {@link URLClassLoader} that encloses an instrumentation plugin, and
 * provides the following functionalities:
 * <ol>
 * <li>{@link #isCompatible(ClassLoader)}: Determines whether the
 * instrumentation plugin it repserents is compatible with a specified
 * {@code ClassLoader}.</li>
 * <li>{@link #markFindResource(ClassLoader,String)}: Keeps track of the
 * names of classes that have been loaded into a specified
 * {@code ClassLoader}.</li>
 * </ol>
 */
class PluginClassLoader extends URLClassLoader {
  private static final Logger logger = Logger.getLogger(PluginClassLoader.class.getName());

  private final Map<ClassLoader,Boolean> compatibility = new IdentityHashMap<>();
  private final Map<ClassLoader,Set<String>> classLoaderToClassName = new IdentityHashMap<>();

  /**
   * Creates a new {@code PluginClassLoader} with the specified classpath URLs
   * and parent {@code ClassLoader}.
   *
   * @param urls The classpath URLs.
   * @param parent The parent {@code ClassLoader}.
   */
  PluginClassLoader(final URL[] urls, final ClassLoader parent) {
    super(urls, parent);
  }

  /**
   * Returns {@code true} if the instrumentation plugin represented by this
   * instance is compatible with its target classes that are loaded in the
   * specified {@code ClassLoader}.
   * <p>
   * This method utilizes the {@link LibraryFingerprint} class to determine
   * compatibility via "fingerprinting".
   * <p>
   * Once "fingerprinting" has been performed, the resulting value is
   * associated with the specified {@code ClassLoader} in the
   * {@link #compatibility} map as a cache.
   *
   * @param classLoader The {@code ClassLoader} for which the instrumentation
   *          plugin represented by this {@code PluginClassLoader} is to be
   *          checked for compatibility.
   * @return {@code true} if the target classes in the specified
   *         {@code ClassLoader} are compatible with the instrumentation
   *         plugin represented by this {@code PluginClassLoader}, and
   *         {@code false} if the specified {@code ClassLoader} is
   *         incompatible.
   */
  boolean isCompatible(final ClassLoader classLoader) {
    final Boolean compatible = compatibility.get(classLoader);
    if (compatible != null)
      return compatible;

    final URL fpURL = getResource("fingerprint.bin");
    if (fpURL != null) {
      try {
        final LibraryFingerprint fingerprint = LibraryFingerprint.fromFile(fpURL);
        final FingerprintError[] errors = fingerprint.isCompatible(classLoader, 0, 0);
        if (errors != null) {
          logger.warning("Disallowing instrumentation due to \"fingerprint.bin mismatch\" errors:\n" + Util.toIndentedString(errors) + " in: " + Util.toIndentedString(getURLs()));
          compatibility.put(classLoader, false);
          return false;
        }

        if (logger.isLoggable(Level.FINE))
          logger.fine("Allowing instrumentation due to \"fingerprint.bin match\" for: " + Util.toIndentedString(getURLs()));
      }
      catch (final IOException e) {
        // TODO: Parameterize the default behavior!
        logger.log(Level.SEVERE, "Resorting to default behavior (permit instrumentation) due to \"fingerprint.bin read error\" in: " + Util.toIndentedString(getURLs()), e);
      }
    }
    else {
      // TODO: Parameterize the default behavior!
      logger.warning("Resorting to default behavior (permit instrumentation) due to \"fingerprint.bin not found\" in: " + Util.toIndentedString(getURLs()));
    }

    compatibility.put(classLoader, true);
    return true;
  }

  /**
   * Marks the specified resource name with {@code true}, associated with the
   * specified {@code ClassLoader}, and returns the previous value of the
   * mark.
   * <p>
   * The first invocation of this method for a specified {@code ClassLoader}
   * and resource name will return {@code false}.
   * <p>
   * Subsequent calls to this method for a specified {@code ClassLoader} and
   * resource name will return {@code true}.
   *
   * @param classLoader The {@code ClassLoader} to which the value of the mark
   *          for the specified resource name is associated.
   * @param resourceName The name of the resource as the target of the mark.
   * @return {@code false} if this method was never called with the specific
   *         {@code ClassLoader} and resource name; {@code true} if this
   *         method was previously called with the specific
   *         {@code ClassLoader} and resource name.
   */
  boolean markFindResource(final ClassLoader classLoader, final String resourceName) {
    Set<String> classNames = classLoaderToClassName.get(classLoader);
    if (classNames == null)
      classLoaderToClassName.put(classLoader, classNames = new HashSet<>());
    else if (classNames.contains(resourceName))
      return true;

    classNames.add(resourceName);
    return false;
  }

  @Override
  public void close() throws IOException {
    super.close();
    compatibility.clear();
    classLoaderToClassName.clear();
  }
}