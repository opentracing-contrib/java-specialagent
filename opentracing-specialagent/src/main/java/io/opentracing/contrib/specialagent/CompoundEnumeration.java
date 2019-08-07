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
 * A useful utility class that will enumerate over an array of enumerations.
 *
 * @param <E> The type parameter of the enumeration.
 * @author Seva Safris
 */
public class CompoundEnumeration<E> implements Enumeration<E> {
  private final Enumeration<E>[] enums;
  private int index = 0;

  /**
   * Creates a new {@code CompoundEnumeration} of the specified enumerations.
   *
   * @param enums The array of {@code Enumeration} objects.
   */
  @SafeVarargs
  public CompoundEnumeration(final Enumeration<E> ... enums) {
    this.enums = enums;
  }

  private boolean next() {
    for (; index < enums.length; ++index)
      if (enums[index] != null && enums[index].hasMoreElements())
        return true;

    return false;
  }

  @Override
  public boolean hasMoreElements() {
    return next();
  }

  @Override
  public E nextElement() {
    if (next())
      return enums[index].nextElement();

    throw new NoSuchElementException();
  }
}