/* Copyright 2019 The OpenTracing Authors
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
import java.util.HashMap;

import net.bytebuddy.dynamic.ClassFileLocator;

/**
 * An implementation of {@link ClassFileLocator} that calls
 * {@link ClassFileLocator#locate(String) classFileLocator.locate(String)}
 * during construction, caching
 * {@link net.bytebuddy.dynamic.ClassFileLocator.Resolution Resolution}s for
 * later retrieval via {@link ClassFileLocator#locate(String)
 * this.locate(String)}.
 *
 * @author Seva Safris
 */
public class CachedClassFileLocator implements ClassFileLocator {
  private final HashMap<String,Resolution> map = new HashMap<>();

  /**
   * Creates a new {@link CachedClassFileLocator} with the specified
   * {@link ClassFileLocator classFileLocator} from which to locate the provided
   * {@code classes}.
   *
   * @param classFileLocator The {@link ClassFileLocator} from which to locate
   *          the provided {@code classes}.
   * @param classes The {@code classes} that should be located and cached from
   *          the specified {@link ClassFileLocator}
   * @throws IOException If an I/O error has occurred.
   */
  public CachedClassFileLocator(final ClassFileLocator classFileLocator, final Class<?> ... classes) throws IOException {
    if (classes == null)
      return;

    for (int i = 0; i < classes.length; ++i) {
      final Class<?> cls = classes[i];
      Resolution resolution = classFileLocator.locate(cls.getName());
      if (resolution instanceof ClassFileLocator.Resolution.Illegal)
        resolution = ClassFileLocator.ForClassLoader.of(cls.getClassLoader()).locate(cls.getName());

      map.put(cls.getName(), resolution);
    }
  }

  @Override
  public void close() throws IOException {
  }

  @Override
  public Resolution locate(final String name) throws IOException {
    return map.get(name);
  }
}