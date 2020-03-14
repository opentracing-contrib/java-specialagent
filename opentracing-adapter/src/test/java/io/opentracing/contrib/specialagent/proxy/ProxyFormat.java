package io.opentracing.contrib.specialagent.proxy;

import io.opentracing.propagation.Format;

public class ProxyFormat<C> implements Format<C> {
  static Class<?> formatClass;
  static Class<?> builtinClass;

  public ProxyFormat(final Format<C> format) {
//    this.format = format;
  }

  public static <C>Object proxy(final Format<C> format) {
    try {
      if (format == Format.Builtin.BINARY)
        return builtinClass.getField("BINARY").get(null);

      if (format == Format.Builtin.HTTP_HEADERS)
        return builtinClass.getField("HTTP_HEADERS").get(null);

      if (format == Format.Builtin.TEXT_MAP)
        return builtinClass.getField("TEXT_MAP").get(null);

      throw new UnsupportedOperationException();
    }
    catch (final IllegalAccessException | NoSuchFieldException e) {
      throw new IllegalStateException(e);
    }
  }
}