package io.opentracing.contrib.specialagent;

/**
 * Represents a predicate (boolean-valued function) of two arguments. This is
 * the two-arity specialization of {@link Predicate}.
 *
 * @param <T> the type of the first argument to the operation
 * @param <U> the type of the second argument to the operation
 * @see Predicate
 */
public interface BiPredicate<T,U> {
  /**
   * Performs this operation on the given arguments.
   *
   * @param t The first input argument.
   * @param u The second input argument.
   */
  boolean test(T t, U u);
}