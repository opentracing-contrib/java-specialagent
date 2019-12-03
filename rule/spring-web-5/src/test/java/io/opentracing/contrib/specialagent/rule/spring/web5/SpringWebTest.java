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

package io.opentracing.contrib.specialagent.rule.spring.web5;

import static org.awaitility.Awaitility.*;
import static org.hamcrest.core.IsEqual.*;
import static org.junit.Assert.*;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.http.ResponseEntity;
import org.springframework.util.concurrent.FailureCallback;
import org.springframework.util.concurrent.ListenableFutureCallback;
import org.springframework.util.concurrent.SuccessCallback;
import org.springframework.web.client.AsyncRestTemplate;
import org.springframework.web.client.RestTemplate;

import io.opentracing.contrib.specialagent.AgentRunner;
import io.opentracing.contrib.specialagent.TestUtil;
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

    await().atMost(15, TimeUnit.SECONDS).until(TestUtil.reportedSpansSize(tracer), equalTo(1));
    assertEquals(1, tracer.finishedSpans().size());
  }

  @Test
  public void testAsyncCallback(final MockTracer tracer) {
    final AsyncRestTemplate restTemplate = new AsyncRestTemplate();
    final AtomicBoolean foundSpan = new AtomicBoolean(false);

    try {
      restTemplate.getForEntity("http://localhost:12345", String.class).addCallback(new ListenableFutureCallback<ResponseEntity<String>>() {
        @Override
        public void onFailure(final Throwable t) {
          foundSpan.set(tracer.activeSpan() != null);
        }

        @Override
        public void onSuccess(final ResponseEntity<String> result) {
          foundSpan.set(tracer.activeSpan() != null);
        }
      });
    }
    catch (final Exception ignore) {
    }

    await().atMost(15, TimeUnit.SECONDS).until(TestUtil.reportedSpansSize(tracer), equalTo(1));
    assertEquals(1, tracer.finishedSpans().size());
    assertTrue(foundSpan.get());
  }

  @Test
  public void testAsyncSuccessCallback(final MockTracer tracer) {
    final AsyncRestTemplate restTemplate = new AsyncRestTemplate();
    final AtomicBoolean foundSpan = new AtomicBoolean(false);

    try {
      restTemplate.getForEntity("http://localhost:12345", String.class).addCallback(new SuccessCallback<ResponseEntity<String>>() {
        @Override
        public void onSuccess(final ResponseEntity<String> result) {
          foundSpan.set(tracer.activeSpan() != null);
        }
      }, new FailureCallback() {
        @Override
        public void onFailure(final Throwable t) {
          foundSpan.set(tracer.activeSpan() != null);
        }
      });
    }
    catch (final Exception ignore) {
    }

    await().atMost(15, TimeUnit.SECONDS).until(TestUtil.reportedSpansSize(tracer), equalTo(1));
    assertEquals(1, tracer.finishedSpans().size());
    assertTrue(foundSpan.get());
  }
}