package io.opentracing.contrib.specialagent;

import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;
import java.util.Map;

abstract class Transformer<T> {
  final String file;

  Transformer(final String file) {
    this.file = file;
  }

  final String getFile() {
    return this.file;
  }

  final Enumeration<URL> getResources() throws IOException {
    return ClassLoader.getSystemClassLoader().getResources(file);
  }

  abstract void loadRules(final ClassLoader allPluginsClassLoader, final Map<String,Integer> pluginJarToIndex, final String arg, final T retransformer) throws IOException;
}