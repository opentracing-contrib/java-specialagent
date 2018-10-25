package io.opentracing.contrib.instrumenter;

import java.io.IOException;
import java.io.InputStream;
import java.util.logging.LogManager;

import org.junit.runners.model.InitializationError;

public class TestRunner extends InstrumenterRunner {
  static {
    final InputStream in = InstrumenterRunner.class.getResourceAsStream("/logging.properties");
    if (in != null) {
      try {
        LogManager.getLogManager().readConfiguration(in);
      }
      catch (final IOException e) {
        throw new ExceptionInInitializerError(e);
      }
    }
  }

  public TestRunner(final Class<?> cls) throws InitializationError {
    super(cls);
  }

  @Override
  protected String getInstrumenterPath() {
    return "target/opentracing-instrumenter-0.0.1-SNAPSHOT-tests.jar";
  }
}