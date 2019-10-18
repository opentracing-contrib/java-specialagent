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

package io.opentracing.contrib.specialagent.spring.scheduling;

import static org.awaitility.Awaitility.*;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import java.util.List;
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

import io.opentracing.contrib.specialagent.AgentRunner;
import io.opentracing.contrib.specialagent.AgentRunner.Config;
import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;
import io.opentracing.tag.Tags;

@RunWith(AgentRunner.class)
@Config(isolateClassLoader = false)
public class SpringAsyncTest {
  @Test
  public void test(final MockTracer tracer) throws ExecutionException, InterruptedException {
    final ApplicationContext context = new AnnotationConfigApplicationContext(SpringAsyncConfiguration.class);
    final SpringAsyncConfiguration configuration = context.getBean(SpringAsyncConfiguration.class);
    final String res = configuration.async().get();
    assertEquals("whatever", res);

    try {
      configuration.asyncException().get();
      fail();
    }
    catch (final Exception ignore) {
    }

    await().atMost(15, TimeUnit.SECONDS).until(() -> tracer.finishedSpans().size(), equalTo(2));
    final List<MockSpan> spans = tracer.finishedSpans();
    assertEquals(2, spans.size());
    for (final MockSpan span : spans) {
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
}