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