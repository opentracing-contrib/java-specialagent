package io.opentracing.contrib.specialagent.hystrix;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import feign.Client;
import feign.Feign;
import feign.Headers;
import feign.RequestLine;
import feign.Retryer;
import feign.Target;
import feign.hystrix.HystrixFeign;
import feign.opentracing.TracingClient;
import io.opentracing.Scope;
import io.opentracing.contrib.specialagent.AgentRunner;
import io.opentracing.contrib.specialagent.AgentRunner.Config;
import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;
import io.opentracing.util.GlobalTracer;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AgentRunner.class)
@Config(isolateClassLoader = false)
public class HystrixTest {
  @Before
  public void before(final MockTracer tracer) {
    tracer.reset();
  }

  @Test
  @AgentRunner.TestConfig(verbose=true)
  public void test(MockTracer tracer) {
    final Feign feign  = HystrixFeign.builder()
        .client(new TracingClient(new Client.Default(null, null), GlobalTracer.get()))
        .retryer(new Retryer.Default(100, SECONDS.toMillis(1), 2))
        .build();

    final MockSpan parent = tracer.buildSpan("parent").start();
    try (final Scope ignore = tracer.activateSpan(parent)) {
      test(feign, tracer);
    }

    for (final MockSpan span : tracer.finishedSpans()) {
      assertEquals(parent.context().traceId(), span.context().traceId());
    }

    assertNull(tracer.activeSpan());
  }

  private static void test(final Feign feign, final MockTracer tracer) {
    final StringEntityRequest entity = feign.newInstance(new Target.HardCodedTarget<>(StringEntityRequest.class, "http://localhost:12345"));
    try {
      final String res = entity.get();
      System.out.println(res);
    }
    catch (final Exception ignore) {
    }

    assertEquals(2, tracer.finishedSpans().size());
  }

  private interface StringEntityRequest {
    @RequestLine("GET")
    @Headers("Content-Type: application/json")
    String get();
  }

}
