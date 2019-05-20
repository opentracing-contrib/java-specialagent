package io.opentracing.contrib.specialagent.feign;

import static org.junit.Assert.assertEquals;

import feign.Client;
import feign.Feign;
import feign.Headers;
import feign.RequestLine;
import feign.Retryer;
import feign.Target;
import io.opentracing.Scope;
import io.opentracing.contrib.specialagent.AgentRunner;
import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AgentRunner.class)
public class FeignTest {

  @Before
  public void before(MockTracer tracer) {
    tracer.reset();
  }

  @Test
  public void testImplicitClient(MockTracer tracer) {
    Feign feign = getImplicitClient();
    test(feign, tracer);
  }

  @Test
  public void testExplicitClient(MockTracer tracer) {
    Feign feign = getExplicitClient();
    test(feign, tracer);
  }

  @Test
  public void testWithParent(MockTracer tracer) {
    Feign feign = getImplicitClient();
    final MockSpan parent = tracer.buildSpan("parent").start();
    try (Scope ignore = tracer.activateSpan(parent)) {
      test(feign, tracer);
    }
    for (MockSpan span : tracer.finishedSpans()) {
      assertEquals(parent.context().traceId(), span.context().traceId());
    }
  }

  private void test(Feign feign, MockTracer tracer) {
    StringEntityRequest
        entity = feign.<StringEntityRequest>newInstance(
        new Target.HardCodedTarget(StringEntityRequest.class,
            "http://localhost:12345"));
    try {
      String res = entity.get();
      System.out.println(res);
    } catch (Exception ignore) {
    }
    assertEquals(2, tracer.finishedSpans().size());
  }

  private static Feign getImplicitClient() {
    return Feign.builder()
        .retryer(new Retryer.Default(100, TimeUnit.SECONDS.toMillis(1), 2))
        .build();
  }

  private static Feign getExplicitClient() {
    return Feign.builder()
        .client((new Client.Default(null, null)))
        .retryer(new Retryer.Default(100, TimeUnit.SECONDS.toMillis(1), 2))
        .build();
  }

  private interface StringEntityRequest {
    @RequestLine("GET")
    @Headers("Content-Type: application/json")
    String get();
  }
}
