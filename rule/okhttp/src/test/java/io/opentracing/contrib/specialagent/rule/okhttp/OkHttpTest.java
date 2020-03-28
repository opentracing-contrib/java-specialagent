/* Copyright 2018 The OpenTracing Authors
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

package io.opentracing.contrib.specialagent.rule.okhttp;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.opentracing.contrib.specialagent.AgentRunner;
import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;
import io.opentracing.util.GlobalTracer;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

@RunWith(AgentRunner.class)
public class OkHttpTest {
  @Before
  public void before(final MockTracer tracer) {
    assertNull(GlobalTracer.class.getClassLoader());
    tracer.reset();
  }

  @Test
  public void testBuilder(final MockTracer tracer) throws IOException {
    final OkHttpClient client = new OkHttpClient.Builder().build();
    test(client, tracer);
  }

  @Test
  public void testConstructor(final MockTracer tracer) throws IOException {
    final OkHttpClient client = new OkHttpClient();
    test(client, tracer);
  }

  private static void test(final OkHttpClient client, final MockTracer tracer) throws IOException {
    try (final MockWebServer server = new MockWebServer()) {
      server.enqueue(new MockResponse().setBody("hello, world!").setResponseCode(200));

      final HttpUrl httpUrl = server.url("/hello");

      final Request request = new Request.Builder().url(httpUrl).build();
      final Response response = client.newCall(request).execute();

      assertEquals(200, response.code());

      final List<MockSpan> finishedSpans = tracer.finishedSpans();
      assertEquals(2, finishedSpans.size());
      assertEquals("GET", finishedSpans.get(0).operationName());
      assertEquals("GET", finishedSpans.get(1).operationName());

      assertEquals(1, client.interceptors().size());
      assertEquals(1, client.networkInterceptors().size());
    }
  }
}