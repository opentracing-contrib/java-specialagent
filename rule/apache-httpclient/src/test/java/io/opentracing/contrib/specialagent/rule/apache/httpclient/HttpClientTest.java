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

package io.opentracing.contrib.specialagent.rule.apache.httpclient;

import static org.junit.Assert.*;

import java.io.IOException;

import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicHttpRequest;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.opentracing.contrib.specialagent.AgentRunner;
import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;
import io.opentracing.tag.Tags;

@RunWith(AgentRunner.class)
public class HttpClientTest {
  @Before
  public void before(final MockTracer tracer) {
    tracer.reset();
  }

  @Test
  public void test(final MockTracer tracer) {
    System.setProperty(Configuration.SPAN_DECORATORS, "io.opentracing.contrib.specialagent.rule.apache.httpclient.ApacheClientSpanDecorator$StandardTags,io.opentracing.contrib.specialagent.rule.apache.httpclient.MockSpanDecorator");

    final CloseableHttpClient httpClient = HttpClients.createDefault();
    final String url = "http://localhost:12345";

    try {
      httpClient.execute(new HttpGet(url));
    }
    catch (final Exception ignore) {
    }

    try {
      httpClient.execute(HttpHost.create(url), new BasicHttpRequest("GET", url));
    }
    catch (final Exception ignore) {
    }

    try {
      httpClient.execute(new HttpGet(url), new ResponseHandler<Object>() {
        @Override
        public Object handleResponse(final HttpResponse response) throws ClientProtocolException, IOException {
          return "done";
        }
      });
    }
    catch (final Exception ignore) {
    }

    assertEquals(3, tracer.finishedSpans().size());
    for (final MockSpan span : tracer.finishedSpans()) {
      assertEquals(HttpClientAgentIntercept.COMPONENT_NAME, span.tags().get(Tags.COMPONENT.getKey()));
      assertEquals(HttpGet.METHOD_NAME, span.tags().get(Tags.HTTP_METHOD.getKey()));
      assertEquals(url, span.tags().get(Tags.HTTP_URL.getKey()));
      assertEquals("localhost", span.tags().get(Tags.PEER_HOSTNAME.getKey()));
      assertEquals(12345, span.tags().get(Tags.PEER_PORT.getKey()));
      assertEquals(MockSpanDecorator.MOCK_TAG_VALUE, span.tags().get(MockSpanDecorator.MOCK_TAG_KEY));
      assertEquals(Tags.SPAN_KIND_CLIENT, span.tags().get(Tags.SPAN_KIND.getKey()));
    }
  }
}