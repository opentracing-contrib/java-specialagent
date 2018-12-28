package io.opentracing.contrib.specialagent;

import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.net.URL;
import java.util.Enumeration;
import java.util.Map;

public abstract class Manager {
  final String file;

  Manager(final String file) {
    this.file = file;
  }

  final String getFile() {
    return this.file;
  }

  final Enumeration<URL> getResources() throws IOException {
    return ClassLoader.getSystemClassLoader().getResources(file);
  }

  abstract void premain(String agentArgs, Instrumentation instrumentation) throws Exception;
  abstract void loadRules(ClassLoader allPluginsClassLoader, Map<String,Integer> pluginJarToIndex, String arg) throws IOException;
  abstract boolean disableTriggers();
  abstract boolean enableTriggers();
}