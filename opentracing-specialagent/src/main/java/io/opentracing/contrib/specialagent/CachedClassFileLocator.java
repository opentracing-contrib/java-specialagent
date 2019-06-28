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
import java.util.Map;

import net.bytebuddy.dynamic.ClassFileLocator;

public class CachedClassFileLocator implements ClassFileLocator {
  private final Map<String,Resolution> map = new HashMap<>();

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