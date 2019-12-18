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

package io.opentracing.contrib.specialagent.rule.googlehttpclient;

import static org.junit.Assert.assertEquals;

import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.javanet.NetHttpTransport;
import io.opentracing.contrib.specialagent.AgentRunner;
import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;
import io.opentracing.tag.Tags;
import java.io.IOException;
import java.net.ConnectException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AgentRunner.class)
public class GoogleHttpClientTest {
  @Before
  public void before(final MockTracer tracer) {
    tracer.reset();
  }

  @Test
  public void request(final MockTracer tracer) throws IOException {
    HttpRequestFactory requestFactory
        = new NetHttpTransport().createRequestFactory();
    HttpRequest request = requestFactory.buildGetRequest(new GenericUrl("https://www.google.com"));
    final int statusCode = request.execute().getStatusCode();
    assertEquals(200, statusCode);

    final List<MockSpan> spans = tracer.finishedSpans();
    assertEquals(1, spans.size());
    assertEquals(GoogleHttpClientAgentIntercept.COMPONENT_NAME,
        spans.get(0).tags().get(Tags.COMPONENT.getKey()));
  }

  @Test
  public void failedRequest(final MockTracer tracer) throws Exception {
    HttpRequestFactory requestFactory
        = new NetHttpTransport().createRequestFactory();
    HttpRequest request = requestFactory.buildGetRequest(new GenericUrl("http://localhost:12345"));
    try {
      final int statusCode = request.execute().getStatusCode();
      assertEquals(200, statusCode);
    } catch (final ConnectException ignore) {
    }

    final List<MockSpan> spans = tracer.finishedSpans();
    assertEquals(1, spans.size());
    assertEquals(GoogleHttpClientAgentIntercept.COMPONENT_NAME,
        spans.get(0).tags().get(Tags.COMPONENT.getKey()));
  }

  @Test
  public void asyncRequest(MockTracer tracer) throws Exception {
    HttpRequestFactory requestFactory
        = new NetHttpTransport().createRequestFactory();
    HttpRequest request = requestFactory.buildGetRequest(new GenericUrl("http://www.google.com"));
    final int statusCode = request.executeAsync().get(15, TimeUnit.SECONDS).getStatusCode();
    assertEquals(200, statusCode);

    final List<MockSpan> spans = tracer.finishedSpans();
    assertEquals(1, spans.size());
    assertEquals(GoogleHttpClientAgentIntercept.COMPONENT_NAME,
        spans.get(0).tags().get(Tags.COMPONENT.getKey()));
  }
}