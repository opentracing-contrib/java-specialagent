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
package io.opentracing.contrib.specialagent.spring.web;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertEquals;

import io.opentracing.contrib.specialagent.AgentRunner;
import io.opentracing.contrib.specialagent.AgentRunner.Config;
import io.opentracing.mock.MockTracer;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.web.client.AsyncRestTemplate;
import org.springframework.web.client.RestTemplate;

@RunWith(AgentRunner.class)
@Config(isolateClassLoader = false)
public class SpringWebTest {

  @Before
  public void before(MockTracer tracer) {
    tracer.reset();
  }

  @Test
  public void test(final MockTracer tracer) {
    RestTemplate restTemplate = new RestTemplate();

    try {
      restTemplate.getForEntity("http://localhost:12345", String.class);
    } catch (Exception ignore) {

    }

    try {
      restTemplate.getForObject("http://localhost:12345", String.class);
    } catch (Exception ignore) {

    }

    assertEquals(2, tracer.finishedSpans().size());
  }

  @Test
  public void testAsync(final MockTracer tracer) throws InterruptedException {
    AsyncRestTemplate restTemplate = new AsyncRestTemplate();

    try {
      restTemplate.getForEntity("http://localhost:12345", String.class).get(15, TimeUnit.SECONDS);
    } catch (Exception ignore) {
    }

    await().atMost(15, TimeUnit.SECONDS).until(reportedSpansSize(tracer), equalTo(1));

    assertEquals(1, tracer.finishedSpans().size());
  }

  static Callable<Integer> reportedSpansSize(final MockTracer tracer) {
    return new Callable<Integer>() {
      @Override
      public Integer call() {
        return tracer.finishedSpans().size();
      }
    };
  }
}
