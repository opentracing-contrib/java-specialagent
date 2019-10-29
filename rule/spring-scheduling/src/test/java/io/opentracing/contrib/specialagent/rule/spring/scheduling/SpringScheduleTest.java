/* Copyright 2019 The OpenTracing Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentracing.contrib.specialagent.rule.spring.scheduling;

import static org.awaitility.Awaitility.*;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import io.opentracing.contrib.specialagent.AgentRunner;
import io.opentracing.contrib.specialagent.AgentRunner.Config;
import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;
import io.opentracing.tag.Tags;

@RunWith(AgentRunner.class)
@Config(isolateClassLoader = false)
public class SpringScheduleTest {
  @Test
  public void test(final MockTracer tracer) {
    final ApplicationContext context = new AnnotationConfigApplicationContext(SpringScheduleConfiguration.class);
    await().atMost(15, TimeUnit.SECONDS).until(() -> tracer.finishedSpans().size(), greaterThanOrEqualTo(1));
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
}
