package io.opentracing.contrib.specialagent.spring.scheduling;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import io.opentracing.contrib.specialagent.AgentRunner;
import io.opentracing.contrib.specialagent.AgentRunner.Config;
import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;
import io.opentracing.tag.Tags;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@RunWith(AgentRunner.class)
@Config(isolateClassLoader = false)
public class SpringScheduleTest {

  @Test
  public void test(MockTracer tracer) {
    final ApplicationContext context = new AnnotationConfigApplicationContext(
        SpringScheduleConfiguration.class);
    await().atMost(15, TimeUnit.SECONDS).until(reportedSpansSize(tracer), greaterThanOrEqualTo(1));
    List<MockSpan> spans = tracer.finishedSpans();
    assertTrue(spans.size() >= 1);
    for (MockSpan span : spans) {
      assertEquals("spring-scheduled", span.tags().get(Tags.COMPONENT.getKey()));
    }
  }

  @Configuration
  @EnableScheduling
  public static class SpringScheduleConfiguration {
    @Scheduled(fixedDelay = 1000)
    public void scheduled() {
      System.out.println("scheduled");
    }
  }

  private static Callable<Integer> reportedSpansSize(final MockTracer tracer) {
    return () -> tracer.finishedSpans().size();
  }
}
