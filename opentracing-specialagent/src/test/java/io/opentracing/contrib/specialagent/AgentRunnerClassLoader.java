package io.opentracing.contrib.specialagent;

import java.net.URL;
import java.net.URLClassLoader;

public class AgentRunnerClassLoader extends URLClassLoader {
  public final URL[] iso;
  public AgentRunnerClassLoader(final URL[] urls, final URL[] iso, final ClassLoader parent) {
    super(urls, parent);
    this.iso = iso;
  }
}
