/* Copyright 2020 The OpenTracing Authors
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

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Utility class for the creation of dynamic proxies.
 */
public final class DynamicProxy {
  public static interface $Wrapper {
    Object _$$object();
    Object _$$wrapper();
  }

  private static void recurse(final Class<?> iface, final Set<Class<?>> set) {
    if (set.contains(iface))
      return;

    set.add(iface);
    for (final Class<?> extended : iface.getInterfaces())
      recurse(extended, set);
  }

  static Set<Class<?>> getAllInterfaces(final Class<?> cls) {
    Class<?> next = cls;
    final Set<Class<?>> ifaces = new HashSet<>();
    do
      for (final Class<?> iface : next.getInterfaces())
        recurse(iface, ifaces);
    while ((next = next.getSuperclass()) != null);
    return ifaces;
  }

  /**
   * Returns a dynamic proxy of the specified target via the provided wrapper.
   * The type of the returned proxy will be the composition of all
   * super-interfaces of the runtime type of the specified target. This means
   * that if {@code <T>} is a concrete class, then the returned types will not
   * be of type {@code <T>}, but rather a composition of all super-interfaces of
   * type {@code <T>}.
   *
   * @param <T> The type parameter of the specified arguments.
   * @param obj The target object instance to wrap.
   * @param wrapper The wrapping object.
   * @return A dynamic proxy of the specified target via the provided wrapper,
   *         or, {@code wrapper} if {@code target} or {@code wrapper} is null,
   *         or if {@code obj == wrapper}.
   */
  @SuppressWarnings("unchecked")
  public static <T>T wrap(final T obj, final T wrapper) {
    if (obj == null || wrapper == null || obj == wrapper)
      return wrapper;

    final Class<?> cls = obj.getClass();
    Objects.requireNonNull(wrapper);
    final Set<Class<?>> ifaces = getAllInterfaces(cls);
    ifaces.add($Wrapper.class);
    return (T)Proxy.newProxyInstance(cls.getClassLoader(), ifaces.toArray(new Class[ifaces.size()]), new InvocationHandler() {
      @Override
      public Object invoke(final Object proxy, final Method method, final Object[] args) throws Throwable {
        if ("_$$object".equals(method.getName()))
          return obj;

        if ("_$$wrapper".equals(method.getName()))
          return wrapper;

        try {
          return method.invoke(wrapper, args);
        }
        catch (final IllegalArgumentException e) {
          return method.invoke(obj, args);
        }
      }
    });
  }

  public static boolean isProxy(final Object obj) {
    return obj instanceof $Wrapper;
  }

  public static boolean isProxy(final Object obj, final Class<?> wrapperClass) {
    if (!(obj instanceof $Wrapper))
      return false;

    return obj instanceof $Wrapper && wrapperClass.isAssignableFrom((($Wrapper)obj)._$$wrapper().getClass());
  }

  private DynamicProxy() {
  }
}