package io.opentracing.contrib.specialagent;

import java.net.URL;
import java.net.URLClassLoader;

public class AgentRunnerClassLoader extends URLClassLoader {
  public final IsoClassLoader isoClassLoader;

  public AgentRunnerClassLoader(final URL[] urls, final URL[] isoUrls, final ClassLoader parent) {
    super(urls, parent);
    this.isoClassLoader = new IsoClassLoader(isoUrls, this);
  }
}