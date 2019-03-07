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

package io.opentracing.contrib.specialagent.okhttp;

import static org.junit.Assert.*;

import java.io.IOException;
import java.net.URLClassLoader;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;

import io.opentracing.Tracer;
import io.opentracing.contrib.specialagent.AgentRunner;
import io.opentracing.contrib.specialagent.Manager.Event;
import io.opentracing.mock.MockSpan;
import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

@RunWith(AgentRunner.class)
@AgentRunner.Config(events=Event.ERROR)
public class OkHttpTest {
  @Test
  public void test(final Tracer tracer) throws IOException {
    try (final MockWebServer server = new MockWebServer()) {
      server.enqueue(new MockResponse().setBody("hello, world!").setResponseCode(200));

      final HttpUrl httpUrl = server.url("/hello");

//      assertEquals(URLClassLoader.class, Interceptor.class.getClassLoader().getClass());
//      assertEquals(URLClassLoader.class, OkHttpClient.class.getClassLoader().getClass());
//      assertEquals(URLClassLoader.class, OkHttpClient.Builder.class.getClassLoader().getClass());
//      assertEquals(URLClassLoader.class, OkHttpClient.class.getClassLoader().getClass());

      // FIXME: Rule does not currently work when just using the `OkHttpClient`
      // FIXME: default constructor.
      final OkHttpClient client = new OkHttpClient.Builder().build();

      final Request request = new Request.Builder().url(httpUrl).build();
      final Response response = client.newCall(request).execute();

      assertEquals(200, response.code());

//      final List<MockSpan> finishedSpans = tracer.finishedSpans();
//      assertEquals(2, finishedSpans.size());
//      assertEquals("GET", finishedSpans.get(0).operationName());
//      assertEquals("GET", finishedSpans.get(1).operationName());
    }
  }
}