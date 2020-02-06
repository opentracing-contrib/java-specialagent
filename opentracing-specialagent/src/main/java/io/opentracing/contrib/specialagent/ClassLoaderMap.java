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

import java.net.URLClassLoader;
import java.util.concurrent.ConcurrentHashMap;

/**
 * An implementation of {@link ConcurrentHashMap} that considers {@code null}
 * keys and values as the bootstrap class loader. Specifically, when
 * {@code key == null}, the key is set to {@link BootProxyClassLoader#INSTANCE};
 * when
 * {@code value instanceof URLClassLoader && value.getURLs().length == 0 && value.getParent() == null},
 * the value is set to {@code null}.
 *
 * @param <T> The value type for the map.
 * @author Seva Safris
 */
class ClassLoaderMap<T> extends ConcurrentHashMap<ClassLoader,T> {
  private static final long serialVersionUID = 5515722666603482519L;
  private static final ClassLoader NULL = BootProxyClassLoader.INSTANCE;

  /**
   * This method is modified to support value lookups where the key is a "proxy"
   * class loader representing the bootstrap class loader. This pattern is used
   * by ByteBuddy, whereby the proxy class loader is an {@code URLClassLoader}
   * that has an empty classpath and a null parent.
   * <p>
   * {@inheritDoc}
   */
  @Override
  @SuppressWarnings("unlikely-arg-type")
  public T get(final Object key) {
    T value = super.get(key == null ? NULL : key);
    if (value != null || !(key instanceof URLClassLoader))
      return value;

    final URLClassLoader classLoader = (URLClassLoader)key;
    return classLoader.getURLs().length > 0 || classLoader.getParent() != null ? null : super.get(NULL);
  }

  @Override
  public T put(final ClassLoader key, final T value) {
    return super.put(key == null ? NULL : key, value);
  }
}