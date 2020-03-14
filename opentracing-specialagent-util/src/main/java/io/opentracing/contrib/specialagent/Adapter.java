package io.opentracing.contrib.specialagent;

import static io.opentracing.contrib.specialagent.Constants.*;

public abstract class Adapter {
  public static boolean isAgentRunner() {
    return System.getProperty(AGENT_RUNNER_ARG) != null;
  }

  public static ClassLoader tracerClassLoader;

  public abstract Object getAgentRunnerTracer();
  public abstract void loadTracer(ClassLoader pluginsClassLoader, ClassLoader isoClassLoader) throws IllegalStateException;
}