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
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * An {@link URLClassLoader} that encloses an instrumentation rule, and provides
 * the following functionalities:
 * <ol>
 * <li>{@link #isCompatible(ClassLoader)}: Determines whether the
 * instrumentation rule it repserents is compatible with a specified
 * {@code ClassLoader}.</li>
 * </ol>
 *
 * @author Seva Safris
 */
class RuleClassLoader extends URLClassLoader {
  private static final Logger logger = Logger.getLogger(RuleClassLoader.class);
  private static final String SKIP_FINGERPRINT = "sa.fingerprint.skip";
  private static final boolean skipFingerprint;

  static {
    final String property = System.getProperty(SKIP_FINGERPRINT);
    skipFingerprint = property != null && !"false".equalsIgnoreCase(property);
  }

  /**
   * Callback that is used to load a class by the specified resource path into
   * the provided {@code ClassLoader}. The {@code ClassNotFoundException}
   * invokes {@link ClassLoaderAgentRule.LoadClass#exit}.
   */
  private static final BiConsumer<String,ClassLoader> loadClass = new BiConsumer<String,ClassLoader>() {
    @Override
    public void accept(final String path, final ClassLoader classLoader) {
      final String className = path.substring(0, path.length() - 6).replace('/', '.');
      if (logger.isLoggable(Level.FINEST))
        logger.finest("Class#forName(\"" + className + "\", false, " + AssembleUtil.getNameId(classLoader) + ")");

      try {
        Class.forName(className, false, classLoader);
      }
      catch (final ClassNotFoundException e) {
      }
    }
  };

  private final Map<ClassLoader,Boolean> compatibility = new IdentityHashMap<>();
  private final Map<ClassLoader,Boolean> preLoaded = new IdentityHashMap<>();
  private final PluginManifest pluginManifest;
  private final IsoClassLoader isoClassLoader;

  /**
   * Creates a new {@code RuleClassLoader} with the specified classpath URLs and
   * parent {@code ClassLoader}.
   *
   * @param pluginManifest The {@link PluginManifest}.
   * @param isoClassLoader {@code IsoClassLoader} supplying classes that are
   *          isolated from parent class loaders.
   * @param parent The parent {@code ClassLoader}.
   * @param files The classpath URLs.
   */
  RuleClassLoader(final PluginManifest pluginManifest, final IsoClassLoader isoClassLoader, final ClassLoader parent, final File ... files) {
    super(AssembleUtil.toURLs(files), parent);
    this.pluginManifest = pluginManifest;
    this.isoClassLoader = isoClassLoader;
    if (parent == null || parent == ClassLoader.getSystemClassLoader())
      preLoaded.put(parent, Boolean.TRUE);
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
    if (preLoaded.containsKey(classLoader))
      return;

    preLoaded.put(classLoader, Boolean.FALSE);
    if (logger.isLoggable(Level.FINE))
      logger.fine("RuleClassLoader<" + AssembleUtil.getNameId(this) + ">#preLoad(" + AssembleUtil.getNameId(classLoader) + ")");

    // Call Class.forName(...) for each class in ruleClassLoader to load in
    // the caller's class loader.
    try {
      AssembleUtil.<ClassLoader>forEachClass(getURLs(), classLoader, loadClass);
    }
    catch (final IOException e) {
      throw new IllegalStateException(e);
    }
    finally {
      preLoaded.put(classLoader, Boolean.TRUE);
    }
  }

  boolean isClosed(final ClassLoader classLoader) {
    final Boolean preLoaded = this.preLoaded.get(classLoader);
    return preLoaded != null && preLoaded;
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
      classLoader = BootProxyClassLoader.INSTANCE;

    Boolean compatible = compatibility.get(classLoader);
    if (compatible != null)
      return compatible;

    try {
      if (!(compatible = isFingerprintCompatible(classLoader)))
        close();

      compatibility.put(classLoader, compatible);
      return compatible;
    }
    catch (final ClassNotFoundException | IllegalAccessException | InvocationTargetException | IOException | NoSuchMethodException e) {
      throw new IllegalStateException(e);
    }
  }

  private boolean isFingerprintCompatible(final ClassLoader classLoader) throws ClassNotFoundException, IllegalAccessException, InvocationTargetException, NoSuchMethodException {
    if (skipFingerprint) {
      if (logger.isLoggable(Level.FINE))
        logger.fine("Allowing instrumentation with \"" + pluginManifest.name + "\" due to \"-D" + SKIP_FINGERPRINT + "=true\"");

      compatibility.put(classLoader, true);
      return true;
    }

    final Class<?> libraryFingerprintClass = Class.forName("io.opentracing.contrib.specialagent.LibraryFingerprint", true, isoClassLoader);
    final Method fromFileMethod = libraryFingerprintClass.getDeclaredMethod("fromFile", URL.class);
    final Object fingerprint = fromFileMethod.invoke(null, pluginManifest.getFingerprint());
    if (fingerprint != null) {
      final Method isCompatibleMethod = libraryFingerprintClass.getDeclaredMethod("isCompatible", ClassLoader.class);
      final Object[] errors = (Object[])isCompatibleMethod.invoke(fingerprint, classLoader);
      if (errors != null) {
        if (logger.isLoggable(Level.FINE))
          logger.fine("Disallowing instrumentation with \"" + pluginManifest.name + "\" due to \"" + UtilConstants.FINGERPRINT_FILE + " mismatch\" errors:\n" + AssembleUtil.toIndentedString(errors) + "\nin:\n" + AssembleUtil.toIndentedString(getURLs()));

        compatibility.put(classLoader, false);
        return false;
      }

      if (logger.isLoggable(Level.FINE))
        logger.fine("Allowing instrumentation with \"" + pluginManifest.name + "\" due to \"" + UtilConstants.FINGERPRINT_FILE + " match\" for:\n" + AssembleUtil.toIndentedString(getURLs()));

      return true;
    }

    if (logger.isLoggable(Level.FINE))
      logger.fine("Allowing instrumentation with \"" + pluginManifest.name + "\" due to \"" + UtilConstants.FINGERPRINT_FILE + " not found\"\nin:\n" + AssembleUtil.toIndentedString(getURLs()));

    return true;
  }
}