package io.opentracing.contrib.web.servlet.filter;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public final class ClassUtil {
  public static Method getMethod(final Class<?> cls, final String name, final Class<?> ... parameterTypes) {
    try {
      return cls.getMethod(name, parameterTypes);
    }
    catch (final NoSuchMethodException e) {
      return null;
    }
  }

  public static boolean invoke(final Object[] returned, final Object obj, final Method method, final Object ... args) {
    if (method == null)
      return false;

    try {
      returned[0] = method.invoke(obj, args);
      return true;
    }
    catch (final IllegalAccessException | InvocationTargetException e) {
      return false;
    }
  }

  private ClassUtil() {
  }
}
