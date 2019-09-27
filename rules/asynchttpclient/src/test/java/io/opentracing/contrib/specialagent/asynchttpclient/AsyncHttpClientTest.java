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

package io.opentracing.contrib.specialagent.asynchttpclient;

import static org.awaitility.Awaitility.*;
import static org.hamcrest.core.IsEqual.*;
import static org.junit.Assert.*;

import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.asynchttpclient.AsyncCompletionHandler;
import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.DefaultAsyncHttpClient;
import org.asynchttpclient.Request;
import org.asynchttpclient.RequestBuilder;
import org.asynchttpclient.Response;
import org.asynchttpclient.util.HttpConstants;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.opentracing.contrib.specialagent.AgentRunner;
import io.opentracing.mock.MockTracer;

@RunWith(AgentRunner.class)
public class AsyncHttpClientTest {
  @Before
  public void before(final MockTracer tracer) {
    tracer.reset();
  }

  @Test
  public void testNoHandler(final MockTracer tracer) throws IOException {
    try (final AsyncHttpClient client = new DefaultAsyncHttpClient()) {
      final Request request = new RequestBuilder(HttpConstants.Methods.GET).setUrl("http://localhost:12345").build();
      try {
        client.executeRequest(request).get(10, TimeUnit.SECONDS);
      }
      catch (final Exception ignore) {
      }
    }

    await().atMost(15, TimeUnit.SECONDS).until(reportedSpansSize(tracer), equalTo(1));
    assertEquals(1, tracer.finishedSpans().size());
    assertNull(tracer.activeSpan());
  }

  @Test
  public void testWithHandler(final MockTracer tracer) throws IOException {
    final AtomicInteger counter = new AtomicInteger();
    try (final AsyncHttpClient client = new DefaultAsyncHttpClient()) {
      final Request request = new RequestBuilder(HttpConstants.Methods.GET).setUrl("http://localhost:12345").build();
      try {
        client.executeRequest(request, new AsyncCompletionHandler<Object>() {
          @Override
          public Object onCompleted(final Response response) {
            assertNotNull(tracer.activeSpan());
            counter.incrementAndGet();
            return response;
          }

          @Override
          public void onThrowable(final Throwable t) {
            assertNotNull(tracer.activeSpan());
            counter.incrementAndGet();
          }
        }).get(10, TimeUnit.SECONDS);
      }
      catch (final Exception ignore) {
      }
    }

    await().atMost(15, TimeUnit.SECONDS).until(reportedSpansSize(tracer), equalTo(1));
    assertEquals(1, tracer.finishedSpans().size());
    assertEquals(1, counter.get());
    assertNull(tracer.activeSpan());
  }

  private static Callable<Integer> reportedSpansSize(final MockTracer tracer) {
    return new Callable<Integer>() {
      @Override
      public Integer call() {
        return tracer.finishedSpans().size();
      }
    };
  }
}