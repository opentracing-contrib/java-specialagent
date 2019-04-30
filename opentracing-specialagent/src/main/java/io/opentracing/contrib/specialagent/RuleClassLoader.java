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

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * An {@link URLClassLoader} that encloses an instrumentation rule, and provides
 * the following functionalities:
 * <ol>
 * <li>{@link #isCompatible(ClassLoader)}: Determines whether the
 * instrumentation rule it repserents is compatible with a specified
 * {@code ClassLoader}.</li>
 * <li>{@link #markFindResource(ClassLoader,String)}: Keeps track of the names
 * of classes that have been loaded into a specified {@code ClassLoader}.</li>
 * </ol>
 *
 * @author Seva Safris
 */
class RuleClassLoader extends URLClassLoader {
  private static final Logger logger = Logger.getLogger(RuleClassLoader.class.getName());

  public static final String FINGERPRINT_FILE = "fingerprint.bin";
  static final ClassLoader BOOT_LOADER_PROXY = new URLClassLoader(new URL[0], null);

  private static final boolean failOnEmptyFingerprint;

  private final Map<ClassLoader,Boolean> compatibility = new IdentityHashMap<>();
  private final Map<ClassLoader,Set<String>> classLoaderToClassName = new IdentityHashMap<>();
  private boolean preLoaded;

  /**
   * Callback that is used to load a class by the specified resource path into
   * the provided {@code ClassLoader}.
   */
  private static final BiConsumer<String,ClassLoader> loadClass = new BiConsumer<String,ClassLoader>() {
    @Override
    public void accept(final String path, final ClassLoader classLoader) {
      if (!path.endsWith(".class") || path.startsWith("META-INF/") || path.startsWith("module-info"))
        return;

      final String className = path.substring(0, path.length() - 6).replace('/', '.');
      try {
        Class.forName(className, false, classLoader);
      }
      catch (final ClassNotFoundException e) {
        logger.log(Level.SEVERE, "Failed to load class " + className + " in " + classLoader, e);
      }
    }
  };

  static {
    final String property = System.getProperty("failOnEmptyFingerprint");
    failOnEmptyFingerprint = property != null && !"false".equalsIgnoreCase(property);
  }

  /**
   * Creates a new {@code RuleClassLoader} with the specified classpath URLs
   * and parent {@code ClassLoader}.
   *
   * @param urls The classpath URLs.
   * @param parent The parent {@code ClassLoader}.
   */
  RuleClassLoader(final URL[] urls, final ClassLoader parent) {
    super(urls, parent);
  }

  @Override
  protected Class<?> findClass(String name) throws ClassNotFoundException {
    return super.findClass(name);
  }

  /**
   * Preloads classes in the {@code RuleClassLoader} by calling
   * {@link Class#forName(String)} on all classes in this class loader. A
   * side-effect of this procedure is that is will load all dependent classes
   * that are also needed to be loaded, which may belong to a different class
   * loader (i.e. the parent, or parent's parent, and so on).
   *
   * @param classLoader The {@code ClassLoader}.
   */
  void preLoad(final ClassLoader classLoader) {
    if (preLoaded)
      return;

    preLoaded = true;
    if (logger.isLoggable(Level.FINE))
      logger.fine("RuleClassLoader<" + SpecialAgentUtil.getIdentityCode(this) + ">#preLoad(ClassLoader<" + SpecialAgentUtil.getIdentityCode(classLoader) + ">)");

    // Call Class.forName(...) for each class in ruleClassLoader to load in
    // the caller's class loader
    for (final URL pathUrl : getURLs()) {
      if (pathUrl.toString().endsWith(".jar")) {
        try (final ZipInputStream zip = new ZipInputStream(pathUrl.openStream())) {
          for (ZipEntry entry; (entry = zip.getNextEntry()) != null;) {
            final String name = entry.getName();
            loadClass.accept(name, classLoader);
          }
        }
        catch (final IOException e) {
          logger.log(Level.SEVERE, "Failed to read from JAR: " + pathUrl, e);
        }
      }
      else {
        final File dir = new File(URI.create(pathUrl.toString()));
        final Path path = dir.toPath();
        SpecialAgentUtil.recurseDir(dir, new Predicate<File>() {
          @Override
          public boolean test(final File file) {
            final String name = path.relativize(file.toPath()).toString();
            loadClass.accept(name, classLoader);
            return true;
          }
        });
      }
    }
  }

  /**
   * Returns {@code true} if the instrumentation rule represented by this
   * instance is compatible with its target classes that are loaded in the
   * specified {@code ClassLoader}.
   * <p>
   * This method utilizes the {@link LibraryFingerprint} class to determine
   * compatibility via "fingerprinting".
   * <p>
   * Once "fingerprinting" has been performed, the resulting value is associated
   * with the specified {@code ClassLoader} in the {@link #compatibility} map as
   * a cache.
   *
   * @param classLoader The {@code ClassLoader} for which the instrumentation
   *          rule represented by this {@code RuleClassLoader} is to be checked
   *          for compatibility.
   * @return {@code true} if the target classes in the specified
   *         {@code ClassLoader} are compatible with the instrumentation rule
   *         represented by this {@code RuleClassLoader}, and {@code false} if
   *         the specified {@code ClassLoader} is incompatible.
   */
  boolean isCompatible(ClassLoader classLoader) {
    if (classLoader == null)
      classLoader = BOOT_LOADER_PROXY;

    Boolean compatible = compatibility.get(classLoader);
    if (compatible != null)
      return compatible;

    try {
      final URL fpURL = getResource(FINGERPRINT_FILE);
      final LibraryFingerprint fingerprint = fpURL == null ? null : LibraryFingerprint.fromFile(fpURL);
      compatible = isCompatible(fingerprint, classLoader);
      compatibility.put(classLoader, compatible);
      return compatible;
    }
    catch (final IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private boolean isCompatible(final LibraryFingerprint fingerprint, final ClassLoader classLoader) {
    if (fingerprint != null) {
      final FingerprintError[] errors = fingerprint.isCompatible(classLoader);
      if (errors != null) {
        logger.warning("Disallowing instrumentation due to \"" + FINGERPRINT_FILE + " mismatch\" errors:\n" + SpecialAgentUtil.toIndentedString(errors) + " in: " + SpecialAgentUtil.toIndentedString(getURLs()));
        compatibility.put(classLoader, false);
        return false;
      }

      if (logger.isLoggable(Level.FINE))
        logger.fine("Allowing instrumentation due to \"" + FINGERPRINT_FILE + " match\" for:\n" + SpecialAgentUtil.toIndentedString(getURLs()));

      return true;
    }

    if (failOnEmptyFingerprint) {
      logger.warning("Disallowing instrumentation due to \"-DfailOnEmptyFingerprint=true\" and \"" + FINGERPRINT_FILE + " not found\" in:\n" + SpecialAgentUtil.toIndentedString(getURLs()));
      compatibility.put(classLoader, false);
      return false;
    }

    if (logger.isLoggable(Level.FINE))
      logger.fine("Allowing instrumentation due to default \"-DfailOnEmptyFingerprint=false\" and \"" + FINGERPRINT_FILE + " not found\" in:\n" + SpecialAgentUtil.toIndentedString(getURLs()));

    return true;
  }

  /**
   * Marks the specified resource name with {@code true}, associated with the
   * specified {@code ClassLoader}, and returns the previous value of the mark.
   * <p>
   * The first invocation of this method for a specified {@code ClassLoader} and
   * resource name will return {@code false}.
   * <p>
   * Subsequent calls to this method for a specified {@code ClassLoader} and
   * resource name will return {@code true}.
   *
   * @param classLoader The {@code ClassLoader} to which the value of the mark
   *          for the specified resource name is associated.
   * @param resourceName The name of the resource as the target of the mark.
   * @return {@code false} if this method was never called with the specific
   *         {@code ClassLoader} and resource name; {@code true} if this method
   *         was previously called with the specific {@code ClassLoader} and
   *         resource name.
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