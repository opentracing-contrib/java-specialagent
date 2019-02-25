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

import java.lang.reflect.Array;
import java.util.logging.Logger;

/**
 * Utility functions for subclasses of {@link AgentPlugin}.
 *
 * @author Seva Safris
 */
public final class AgentPluginUtil {
  public static final Logger logger = Logger.getLogger(AgentPlugin.class.getName());

  public static final ThreadLocal<Integer> latch = new ThreadLocal<Integer>() {
    @Override
    protected Integer initialValue() {
      return 0;
    }
  };

  public static boolean isEnabled() {
    return true;
  }

  /**
   * Returns an array that is the subArray of the provided array.
   *
   * @param <T> Type parameter of object.
   * @param array The specified {@code array}.
   * @param beginIndex The index to become the start of the new array.
   * @param endIndex The index to become the end of the new array.
   * @return The subArray of the specified {@code array}.
   */
  @SuppressWarnings("unchecked")
  public static <T>T[] subArray(final T[] array, final int beginIndex, final int endIndex) {
    if (endIndex < beginIndex)
      throw new IllegalArgumentException("endIndex (" + endIndex + ") < beginIndex (" + beginIndex + ")");

    final Class<?> componentType = array.getClass().getComponentType();
    final T[] subArray = (T[])Array.newInstance(componentType, endIndex - beginIndex);
    if (beginIndex == endIndex)
      return subArray;

    System.arraycopy(array, beginIndex, subArray, 0, endIndex - beginIndex);
    return subArray;
  }

  /**
   * Returns an array that is the subArray of the provided array. Calling this
   * method is the equivalent of calling Arrays.subArray(array, beginIndex,
   * array.length).
   *
   * @param <T> Type parameter of object.
   * @param array The specified {@code array}.
   * @param beginIndex The index to become the start of the new array.
   * @return The subArray of the specified {@code array}.
   */
  public static <T>T[] subArray(final T[] array, final int beginIndex) {
    return subArray(array, beginIndex, array.length);
  }

  private static class CallingClass extends SecurityManager {
    @Override
    public Class<?>[] getClassContext() {
      return super.getClassContext();
    }
  }

  /**
   * Returns the current execution stack as an array of classes.
   * <p>
   * The length of the array is the number of methods on the execution stack.
   * The element at index {@code 0} is the class of the currently executing
   * method, the element at index {@code 1} is the class of that method's
   * caller, and so on.
   *
   * @return The current execution stack as an array of classes.
   */
  public static Class<?>[] getExecutionStack() {
    return subArray(new CallingClass().getClassContext(), 3);
  }

  /**
   * Returns the current execution stack as an array of
   * {@link StackTraceElement} objects.
   * <p>
   * The length of the array is the number of methods on the execution stack.
   * The element at index {@code 0} is the {@code StackTraceElement} of the
   * currently executing method, the element at index {@code 1} is the
   * {@code StackTraceElement} of that method's caller, and so on.
   *
   * @return The current execution stack as an array of
   *         {@link StackTraceElement} objects.
   */
  public static StackTraceElement[] getCallStack() {
    return subArray(Thread.currentThread().getStackTrace(), 2);
  }

  /**
   * Tests whether the name of the method at the specified {@code frameIndex} in
   * the call stack matches the provided {@code name}.
   *
   * @param name The {@code className + "." + methodName} to match.
   * @param frameIndex The index of the stack frame to check.
   * @return {@code true} if the name of the method at the specified
   *         {@code frameIndex} in the call stack matches the provided
   *         {@code name}; otherwise {@code false}.
   */
  public static boolean callerEquals(final String name, int frameIndex) {
    frameIndex += 2;
    final StackTraceElement[] stackTraceElements = Thread.currentThread().getStackTrace();
    if (stackTraceElements.length < frameIndex)
      return false;

    final StackTraceElement stackTraceElement = stackTraceElements[frameIndex];
    return (stackTraceElement.getClassName() + "." + stackTraceElement.getMethodName()).equals(name);
  }

  private AgentPluginUtil() {
  }
}