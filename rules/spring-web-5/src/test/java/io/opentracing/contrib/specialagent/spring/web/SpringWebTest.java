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

import static org.awaitility.Awaitility.*;
import static org.hamcrest.core.IsEqual.*;
import static org.junit.Assert.*;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.web.client.AsyncRestTemplate;
import org.springframework.web.client.RestTemplate;

import io.opentracing.contrib.specialagent.AgentRunner;
import io.opentracing.mock.MockTracer;

@RunWith(AgentRunner.class)
@SuppressWarnings("deprecation")
public class SpringWebTest {
  @Before
  public void before(final MockTracer tracer) {
    tracer.reset();
  }

  @Test
  public void testSync(final MockTracer tracer) {
    final RestTemplate restTemplate = new RestTemplate();

    try {
      restTemplate.getForEntity("http://localhost:12345", String.class);
    }
    catch (final Exception ignore) {
    }

    try {
      restTemplate.getForObject("http://localhost:12345", String.class);
    }
    catch (final Exception ignore) {
    }

    assertEquals(2, tracer.finishedSpans().size());
  }

  @Test
  public void testAsync(final MockTracer tracer) {
    final AsyncRestTemplate restTemplate = new AsyncRestTemplate();

    try {
      restTemplate.getForEntity("http://localhost:12345", String.class).get(15, TimeUnit.SECONDS);
    }
    catch (final Exception ignore) {
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