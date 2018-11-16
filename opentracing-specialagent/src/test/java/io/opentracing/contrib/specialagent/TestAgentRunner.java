package io.opentracing.contrib.specialagent;

import org.junit.runners.model.InitializationError;

public class TestRunner extends AgentRunner {
  public TestRunner(final Class<?> cls) throws InitializationError {
    super(cls);
  }

  @Override
  protected String getAgentPath() {
    return "target/opentracing-specialagent-0.0.1-SNAPSHOT-tests.jar";
  }
}