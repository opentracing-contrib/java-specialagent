package io.opentracing.contrib.specialagent.spring.scheduling;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import io.opentracing.contrib.specialagent.AgentRunner;
import io.opentracing.contrib.specialagent.AgentRunner.Config;
import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;
import io.opentracing.tag.Tags;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.AsyncResult;
import org.springframework.scheduling.annotation.EnableAsync;

@RunWith(AgentRunner.class)
@Config(isolateClassLoader = false)
public class SpringAsyncTest {

  @Test
  public void test(MockTracer tracer) throws ExecutionException, InterruptedException {
    final ApplicationContext context = new AnnotationConfigApplicationContext(
        SpringAsyncConfiguration.class);
    final SpringAsyncConfiguration configuration = context.getBean(SpringAsyncConfiguration.class);
    final String res = configuration.async().get();
    assertEquals("whatever", res);

    try {
      configuration.asyncException().get();
      fail();
    } catch (Exception ignore) {
    }

    await().atMost(15, TimeUnit.SECONDS).until(reportedSpansSize(tracer), equalTo(2));
    List<MockSpan> spans = tracer.finishedSpans();
    assertEquals(2, spans.size());
    for (MockSpan span : spans) {
      assertEquals("spring-async", span.tags().get(Tags.COMPONENT.getKey()));
    }
  }

  @Configuration
  @EnableAsync
  public static class SpringAsyncConfiguration {
    @Async
    public Future<String> async() {
      return new AsyncResult<>("whatever");
    }

    @Async
    public Future<String> asyncException() {
      throw new RuntimeException("error");
    }
  }

  private static Callable<Integer> reportedSpansSize(final MockTracer tracer) {
    return () -> tracer.finishedSpans().size();
  }
}
