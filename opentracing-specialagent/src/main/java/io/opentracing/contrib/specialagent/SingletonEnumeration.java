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

import java.util.Enumeration;
import java.util.NoSuchElementException;

/**
 * An enumeration that contains a single element.
 *
 * @param <T> The type parameter of the contained element.
 */
public class SingletonEnumeration<T> implements Enumeration<T> {
  private final T element;
  private boolean hasMore = true;

  /**
   * Creates a new {@code SingletonEnumeration} of the specified element.
   *
   * @param element The element.
   */
  public SingletonEnumeration(final T element) {
    this.element = element;
  }

  @Override
  public boolean hasMoreElements() {
    return hasMore;
  }

  @Override
  public T nextElement() {
    if (!hasMore)
      throw new NoSuchElementException();

    hasMore = false;
    return element;
  }
}