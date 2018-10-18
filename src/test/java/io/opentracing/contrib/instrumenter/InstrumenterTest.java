package io.opentracing.contrib.instrumenter;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.BeforeClass;

import io.opentracing.Tracer;
import io.opentracing.contrib.audit.AuditSpan;
import io.opentracing.contrib.audit.LoggingTracer;
import io.opentracing.util.GlobalTracer;

public abstract class InstrumenterTest {
  private static List<AuditSpan> finishedSpans = new ArrayList<>();

  public static class MockTracer extends LoggingTracer {
    @Override
    public void onFinish(final AuditSpan span, final long finishMicros) {
      super.onFinish(span, finishMicros);
      finishedSpans.add(span);
    }
  }

  private static final Tracer tracer = new MockTracer();

  @BeforeClass
  public static void initClass() throws Exception {
    GlobalTracer.register(tracer);
  }

  @Before
  public void before() {
    finishedSpans.clear();
  }

  public static List<AuditSpan> getFinishedSpans() {
    return finishedSpans;
  }

  public static Tracer getTracer() {
    return tracer;
  }
}