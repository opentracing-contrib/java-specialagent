package io.opentracing.contrib.specialagent;

import java.lang.reflect.Array;
import java.util.Arrays;

/**
 * Utility functions for common operations in subclasses of {@link Digest}.
 */
final class DigestUtil {
  /**
   * Returns a string representation of the specified array, using the specified
   * delimiter between the string representation of each element. If the
   * specified array is null, this method returns the string {@code "null"}. If
   * the length of the specified array is 0, this method returns {@code ""}.
   *
   * @param a The array.
   * @param del The delimiter.
   * @return A string representation of the specified array, using the specified
   *         delimiter between the string representation of each element.
   */
  static String toString(final Object[] a, final String del) {
    if (a == null)
      return "null";

    if (a.length == 0)
      return "";

    final StringBuilder builder = new StringBuilder();
    for (int i = 0; i < a.length; ++i) {
      if (i > 0)
        builder.append(del);

      builder.append(String.valueOf(a[i]));
    }

    return builder.toString();
  }

  /**
   * Returns the name of the specified {@code Class} as per the following rules:
   * <ul>
   * <li>If {@code cls} represents {@code void}, this method returns
   * {@code null}</li>
   * <li>If {@code cls} represents an array, this method returns the code
   * semantics representation (i.e. {@code java.lang.Object[]})</li>
   * <li>Otherwies, this method return {@code cls.getName()}</li>
   * </ul>
   *
   * @param cls The class.
   * @return The name of the specified {@code Class}
   */
  static String getName(final Class<?> cls) {
    return cls == Void.TYPE ? null : cls.isArray() ? cls.getComponentType().getName() + "[]" : cls.getName();
  }

  /**
   * Returns an array of {@code String} class names by calling
   * {@link #getName(Class)}) on each element in the specified array of
   * {@code Class} objects; If the length of the specified array is 0, this
   * method returns {@code null}.
   *
   * @param classes The array of {@code Class} objects..
   * @return An array of {@code String} class names by calling
   *         {@link #getName(Class)}) on each element in the specified array of
   *         {@code Class} objects; If the length of the specified array is 0,
   *         this method returns {@code null}.
   * @throws NullPointerException If {@code classes} is null.
   */
  static String[] getNames(final Class<?>[] classes) {
    if (classes.length == 0)
      return null;

    final String[] names = new String[classes.length];
    for (int i = 0; i < classes.length; ++i)
      names[i] = DigestUtil.getName(classes[i]);

    return names;
  }

  /**
   * Sorts the specified array of objects into ascending order, according to the
   * natural ordering of its elements. All elements in the array must implement
   * the {@link Comparable} interface. Furthermore, all elements in the array
   * must be mutually comparable (that is, {@code e1.compareTo(e2)} must not
   * throw a {@link ClassCastException} for any elements {@code e1} and
   * {@code e2} in the array).
   *
   * @param array The array to be sorted.
   * @return The specified array, which is sorted in-place (unless it is null).
   * @see Arrays#sort(Object[])
   */
  static <T>T[] sort(final T[] array) {
    if (array == null)
      return null;

    Arrays.sort(array);
    return array;
  }

  /**
   * Returns an array of type {@code <T>} that includes only the elements that
   * belong to the first and second specified array (the specified arrays must
   * be sorted).
   * <p>
   * <i><b>Note:</b> This is a recursive algorithm, implemented to take
   * advantage of the high performance of callstack registers, but will fail due
   * to a {@link StackOverflowError} if the number of differences between the
   * first and second specified arrays approaches ~80,000.</i>
   *
   * @param <T> Type parameter of array.
   * @param a The first specified array (sorted).
   * @param b The second specified array (sorted).
   * @param i The starting index of the first specified array (should be set to
   *          0).
   * @param j The starting index of the second specified array (should be set to
   *          0).
   * @param r The starting index of the resulting array (should be set to 0).
   * @return An array of type {@code <T>} that includes only the elements that
   *         belong to the first and second specified array (the specified
   *         arrays must be sorted).
   * @throws NullPointerException If {@code a} or {@code b} are null.
   */
  @SuppressWarnings("unchecked")
  static <T extends Comparable<T>>T[] retain(final T[] a, final T[] b, final int i, final int j, final int r) {
    for (int d = 0;; ++d) {
      int comparison = 0;
      if (i + d == a.length || j + d == b.length || (comparison = a[i + d].compareTo(b[j + d])) != 0) {
        final T[] retained;
        if (i + d == a.length || j + d == b.length)
          retained = r + d == 0 ? null : (T[])Array.newInstance(a.getClass().getComponentType(), r + d);
        else if (comparison < 0)
          retained = retain(a, b, i + d + 1, j + d, r + d);
        else
          retained = retain(a, b, i + d, j + d + 1, r + d);

        if (d > 0)
          System.arraycopy(a, i, retained, r, d);

        return retained;
      }
    }
  }

  private DigestUtil() {
  }
}