/* Copyright 2019 The OpenTracing Authors
 *
 * Licensed under the Apache License, final Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, final either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentracing.contrib.specialagent;

import java.io.Serializable;
import java.util.AbstractList;
import java.util.List;
import java.util.Objects;

/**
 * A {@link List} that delegates all method calls to the list specified in the
 * constructor, and overrides {@link List#indexOf(Object)} and
 * {@link List#lastIndexOf(Object)} to test for reference equality instead of
 * object equality.
 *
 * @author Seva Safris
 * @param <E> The type of elements in this list.
 * @see #indexOf(Object)
 * @see #lastIndexOf(Object)
 */
public class IdentityList<E> extends AbstractList<E> implements Serializable {
  private static final long serialVersionUID = 4868018708229533624L;

  private final List<E> source;

  /**
   * Creates a new {@code IdentityList} with the specified list to which method
   * calls are to be delegated.
   *
   * @param source The list to which method calls are to be delegated.
   * @throws NullPointerException If the specified list is null.
   */
  public IdentityList(final List<E> source) {
    this.source = Objects.requireNonNull(source);
  }

  @Override
  public E get(final int index) {
    return source.get(index);
  }

  @Override
  public E set(final int index, final E element) {
    return source.set(index, element);
  }

  @Override
  public void add(final int index, final E element) {
    source.add(index, element);
  }

  @Override
  public E remove(final int index) {
    return source.remove(index);
  }

  @Override
  public int indexOf(final Object o) {
    final int size = size();
    for (int i = 0; i < size; ++i)
      if (source.get(i) == o)
        return i;

    return -1;
  }

  @Override
  public int lastIndexOf(final Object o) {
    for (int i = size() - 1; i >= 0; --i)
      if (source.get(i) == o)
        return i;

    return -1;
  }

  @Override
  public int size() {
    return source.size();
  }
}