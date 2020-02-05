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

import java.io.File;
import java.lang.reflect.Array;
import java.net.MalformedURLException;
import java.net.URL;

import net.bytebuddy.description.method.MethodDescription.AbstractBase;
import net.bytebuddy.description.type.TypeDefinition;

/**
 * Utility functions for subclasses of {@link AgentRule}.
 *
 * @author Seva Safris
 */
public final class AgentRuleUtil {
  public static ClassLoader tracerClassLoader;

  /**
   * Returns the name of the class of the specified object suffixed with
   * {@code '@'} followed by the hexadecimal representation of the object's
   * identity hash code, or {@code "null"} if the specified object is null.
   *
   * @param obj The object.
   * @return The name of the class of the specified object suffixed with
   *         {@code '@'} followed by the hexadecimal representation of the
   *         object's identity hash code, or {@code "null"} if the specified
   *         object is null.
   * @see #getSimpleNameId(Object)
   */
  public static String getNameId(final Object obj) {
    return obj != null ? obj.getClass().getName() + "@" + Integer.toString(System.identityHashCode(obj), 16) : "null";
  }

  /**
   * Returns the simple name of the class of the specified object suffixed with
   * {@code '@'} followed by the hexadecimal representation of the object's
   * identity hash code, or {@code "null"} if the specified object is null.
   *
   * @param obj The object.
   * @return The simple name of the class of the specified object suffixed with
   *         {@code '@'} followed by the hexadecimal representation of the
   *         object's identity hash code, or {@code "null"} if the specified
   *         object is null.
   * @see #getNameId(Object)
   */
  public static String getSimpleNameId(final Object obj) {
    return obj != null ? obj.getClass().getSimpleName() + "@" + Integer.toString(System.identityHashCode(obj), 16) : "null";
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
   * @param frameIndex The index of the stack frame to check.
   * @param name The {@code className + "." + methodName} to match.
   * @return {@code true} if the name of the method at the specified
   *         {@code frameIndex} in the call stack matches the provided
   *         {@code name}; otherwise {@code false}.
   */
  public static boolean callerEquals(int frameIndex, final String name) {
    frameIndex += 2;
    final StackTraceElement[] stackTraceElements = Thread.currentThread().getStackTrace();
    if (stackTraceElements.length <= frameIndex)
      return false;

    final StackTraceElement stackTraceElement = stackTraceElements[frameIndex];
    final String element = stackTraceElement.getClassName() + "." + stackTraceElement.getMethodName();
    return element.equals(name);
  }

  /**
   * Tests whether the name of the method at the specified {@code frameIndex} in
   * the call stack matches the provided {@code name}.
   *
   * @param startFrame The start index of the stack frame to check.
   * @param endFrame The end index (exclusive) of the stack frame to check.
   * @param name The {@code className + "." + methodName} to match.
   * @return {@code true} if the name of the method at the specified
   *         {@code frameIndex} in the call stack matches the provided
   *         {@code name}; otherwise {@code false}.
   */
  public static boolean callerEquals(int startFrame, int endFrame, final String name) {
    startFrame += 2;
    endFrame += 2;

    final StackTraceElement[] stackTraceElements = Thread.currentThread().getStackTrace();
    if (stackTraceElements.length <= startFrame)
      return false;

    if (stackTraceElements.length < endFrame)
      endFrame = stackTraceElements.length;

    for (int i = startFrame; i < endFrame; ++i) {
      final StackTraceElement stackTraceElement = stackTraceElements[i];
      final String element = stackTraceElement.getClassName() + "." + stackTraceElement.getMethodName();
      if (element.equals(name))
        return true;
    }

    return false;
  }

  /**
   * Tests whether the name of the method at the specified {@code frameIndex} in
   * the call stack matches the provided {@code name}.
   *
   * @param frameIndex The index of the stack frame to check.
   * @param names The array of {@code className + "." + methodName} to match.
   * @return {@code true} if the name of the method at the specified
   *         {@code frameIndex} in the call stack matches the provided
   *         {@code name}; otherwise {@code false}.
   */
  public static boolean callerEquals(int frameIndex, final String ... names) {
    frameIndex += 2;
    final StackTraceElement[] stackTraceElements = Thread.currentThread().getStackTrace();
    if (stackTraceElements.length <= frameIndex)
      return false;

    final StackTraceElement stackTraceElement = stackTraceElements[frameIndex];
    final String element = stackTraceElement.getClassName() + "." + stackTraceElement.getMethodName();
    for (int i = 0; i < names.length; ++i)
      if (element.equals(names[i]))
        return true;

    return false;
  }

  /**
   * Tests whether the name of the method at the specified {@code frameIndex} in
   * the call stack matches the provided {@code name}.
   *
   * @param startFrame The start index of the stack frame to check.
   * @param endFrame The end index (exclusive) of the stack frame to check.
   * @param names The array of {@code className + "." + methodName} to match.
   * @return {@code true} if the name of the method at the specified
   *         {@code frameIndex} in the call stack matches the provided
   *         {@code name}; otherwise {@code false}.
   */
  public static boolean callerEquals(int startFrame, int endFrame, final String ...  names) {
    startFrame += 2;
    endFrame += 2;

    final StackTraceElement[] stackTraceElements = Thread.currentThread().getStackTrace();
    if (stackTraceElements.length <= startFrame)
      return false;

    if (stackTraceElements.length < endFrame)
      endFrame = stackTraceElements.length;

    for (int i = startFrame; i < endFrame; ++i) {
      final StackTraceElement stackTraceElement = stackTraceElements[i];
      final String element = stackTraceElement.getClassName() + "." + stackTraceElement.getMethodName();
      for (int j = 0; j < names.length; ++j)
        if (element.equals(names[j]))
          return true;
    }

    return false;
  }

  /**
   * Tests the specified {@link TypeDefinition typeDefinition} whether it or
   * its superclasses define a method by the provided {@code methodName}.
   *
   * @param typeDefinition The {@link TypeDefinition}.
   * @param methodName The method name.
   * @return {@code true} if the specified {@link TypeDefinition
   *         typeDefinition} or its superclasses define a method by the
   *         provided {@code methodName}, otherwise {@code false}.
   */
  public static boolean hasMethodNamed(TypeDefinition typeDefinition, final String methodName) {
    do {
      for (final Object method : typeDefinition.getDeclaredMethods()) {
        if (methodName.equals(((AbstractBase)method).getActualName())) {
          return true;
        }
      }
    }
    while (typeDefinition.getSuperClass() != null && !"java.lang.Object".equals((typeDefinition = typeDefinition.getSuperClass()).getActualName()));
    return false;
  }

  @SuppressWarnings("unchecked")
  public static <T>T getFieldInBootstrapClass(final Class<?> cls, final String fieldName) {
    try {
      return (T)BootProxyClassLoader.INSTANCE.loadClass(cls.getName()).getField(fieldName).get(null);
    }
    catch (final ClassNotFoundException | IllegalAccessException | NoSuchFieldException e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  /**
   * Tests whether the specified execution {@code callStack} stems from a class
   * belonging to the provided {@link ClassLoader classLoader}.
   *
   * @param callStack The call stack, as provided by
   *          {@link SecurityManager#getClassContext}.
   * @param classLoader The {@link ClassLoader}.
   * @return {@code true} if the specified execution {@code callStack} stems
   *         from a class belonging to the provided {@link ClassLoader
   *         classLoader}, otherwise {@code false}.
   */
  public static boolean isFromClassLoader(final Class<?>[] callStack, final ClassLoader classLoader) {
    ClassLoader parent;
    for (final Class<?> cls : callStack) {
      parent = cls.getClassLoader();
      do {
        if (parent == classLoader)
          return true;

        if (parent == null)
          break;

        parent = parent.getParent();
      }
      while (true);
    }

    return false;
  }

  /**
   * Returns an array of {@code URL} objects representing each path entry in the
   * specified {@code classpath}.
   *
   * @param classpath The classpath which to convert to an array of {@code URL}
   *          objects.
   * @return An array of {@code URL} objects representing each path entry in the
   *         specified {@code classpath}.
   */
  public static URL[] classPathToURLs(final String classpath) {
    if (classpath == null)
      return null;

    try {
      final String[] paths = classpath.split(File.pathSeparator);
      final URL[] files = new URL[paths.length];
      for (int i = 0; i < paths.length; ++i)
        files[i] = new URL("file", "", paths[i].endsWith(".jar") || paths[i].endsWith("/") ? paths[i] : paths[i] + "/");

      return files;
    }
    catch (final MalformedURLException e) {
      throw new IllegalStateException(e);
    }
  }

  private AgentRuleUtil() {
  }
}