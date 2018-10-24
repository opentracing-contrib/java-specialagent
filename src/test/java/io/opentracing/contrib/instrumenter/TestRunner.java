package io.opentracing.contrib.instrumenter;

import org.junit.runners.model.InitializationError;

public class TestRunner extends InstrumenterRunner {
  public TestRunner(final Class<?> cls) throws InitializationError {
    super(cls);
  }

  @Override
  protected String getInstrumenterPath() {
    return "target/opentracing-instrumenter-0.0.1-SNAPSHOT-tests.jar";
  }
}