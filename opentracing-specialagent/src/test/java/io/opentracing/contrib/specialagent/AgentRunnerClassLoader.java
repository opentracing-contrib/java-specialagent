package io.opentracing.contrib.specialagent;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;

public class AgentRunnerClassLoader extends URLClassLoader {
  public final File[] ruleFiles;
  public final IsoClassLoader isoClassLoader;

  public AgentRunnerClassLoader(final URL[] classPath, final File[] ruleFiles, final URL[] isoUrls, final ClassLoader parent) {
    super(classPath, parent);
    this.ruleFiles = ruleFiles;
    this.isoClassLoader = new IsoClassLoader(isoUrls, this);
  }
}