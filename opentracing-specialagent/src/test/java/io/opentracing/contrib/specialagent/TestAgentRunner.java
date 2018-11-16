package io.opentracing.contrib.specialagent;

import org.junit.runners.model.InitializationError;

public class TestAgentRunner extends AgentRunner {
  public TestAgentRunner(final Class<?> cls) throws InitializationError {
    super(cls);
  }

  @Override
  protected String getAgentPath() {
    return "target/opentracing-specialagent-0.0.1-SNAPSHOT-tests.jar";
  }
}