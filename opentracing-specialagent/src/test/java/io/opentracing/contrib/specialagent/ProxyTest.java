package io.opentracing.contrib.specialagent;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.HashSet;
import java.util.Set;

import org.junit.Test;

import io.opentracing.propagation.Format;

public class ProxyTest {
  public static <T>T proxy(final T obj) {
    try {
      final ClassLoader targetClassLoader = Thread.currentThread().getContextClassLoader();
      final Class<?> targetClass = Class.forName(obj.getClass().getName(), false, targetClassLoader);
      final Object o = Proxy.newProxyInstance(targetClassLoader, targetClass.getInterfaces(), new InvocationHandler() {
        @Override
        public Object invoke(final Object proxy, final Method method, final Object[] args) throws Throwable {
          if (args == null || args.length == 0) {
            System.err.println(targetClassLoader + " -> " + obj.getClass().getClassLoader());
            return method.invoke(obj);
          }

          final Object[] proxyArgs = new Object[args.length];
          for (int i = 0; i < args.length; ++i) {
            final Object arg = args[i];
            proxyArgs[i] = arg == null || args.getClass().getClassLoader() == targetClassLoader ? arg : proxy(arg);
          }

          System.err.println(targetClassLoader + " -> " + obj.getClass().getClassLoader());
          return method.invoke(obj, proxyArgs);
        }
      });

      return (T)o;
    }
    catch (final ClassNotFoundException e) {
      throw new IllegalStateException(e);
    }
  }

  @Test
  public void test() throws Exception {
    final Class<?> class0 = Format.Builtin.class;

    final URLClassLoader classLoader = new URLClassLoader(new URL[0], null) {
      final Set<String> loaded = new HashSet<>();

      @Override
      protected Class<?> findClass(final String name) throws ClassNotFoundException {
        if (loaded.contains(name))
          return super.findClass(name);

        final byte[] bytes = AssembleUtil.readBytes(ClassLoader.getSystemClassLoader().getResource(name.replace('.', '/').concat(".class")));
        try {
          return defineClass(name, bytes, 0, bytes.length);
        }
        finally {
          loaded.add(name);
        }
      }
    };

    final Class<?> proxy0 = ProxyTest.class;
    final Class<?> proxy1 = Class.forName(ProxyTest.class.getName(), false, classLoader);

    final Class<?> class1 = Class.forName(class0.getName(), false, classLoader);
    final Object obj = class1.getField("TEXT_MAP").get(null);

    Thread.currentThread().setContextClassLoader(proxy0.getClassLoader());
    final Object customToSystem = proxy0.getMethod("proxy", Object.class).invoke(null, obj);
    System.out.println(customToSystem.toString());

    Thread.currentThread().setContextClassLoader(proxy1.getClassLoader());
    final Object systemToCustom = proxy1.getMethod("proxy", Object.class).invoke(null, Format.Builtin.HTTP_HEADERS);
    System.out.println(systemToCustom.toString());
  }
}