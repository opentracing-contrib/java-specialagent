package io.opentracing.contrib.specialagent;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.ClassFileLocator.Resolution.Illegal;

public class CachedClassFileLocator implements ClassFileLocator {
  private final Map<String,Resolution> map = new HashMap<>();

  public CachedClassFileLocator(final ClassFileLocator classFileLocator, final Class<?> ... classes) throws IOException {
    for (final Class<?> cls : classes) {
      Resolution resolution = classFileLocator.locate(cls.getName());
      if (resolution instanceof Illegal)
        resolution = ClassFileLocator.ForClassLoader.of(cls.getClassLoader()).locate(cls.getName());

      map.put(cls.getName(), resolution);
    }
  }

  @Override
  public void close() throws IOException {
  }

  @Override
  public Resolution locate(final String name) throws IOException {
    return map.get(name);
  }
}