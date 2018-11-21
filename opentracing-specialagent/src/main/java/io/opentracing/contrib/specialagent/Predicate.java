package io.opentracing.contrib.specialagent;

/**
 * Represents a predicate (boolean-valued function) of one argument.
 *
 * @param <T> The type of the input to the operation.
 */
public interface Predicate<T> {
  /**
   * Evaluates this predicate on the given argument.
   *
   * @param t The input argument.
   * @return {@code true} if the input argument matches the predicate, otherwise
   *         {@code false}.
   */
  boolean test(T t);
}