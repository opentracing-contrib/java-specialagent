package io.opentracing.contrib.instrumenter;

import org.junit.Before;
import org.junit.BeforeClass;

import io.opentracing.Tracer;
import io.opentracing.util.GlobalTracer;

public abstract class InstrumenterTest {
  private static final Tracer tracer = new LoggingTracer();

  @BeforeClass
  public static void initClass() throws Exception {
    GlobalTracer.register(tracer);
  }

  @Before
  public void init() {
//    tracer.reset();
  }

  public static Tracer getTracer() {
    return tracer;
  }
}