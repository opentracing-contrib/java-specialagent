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

package io.opentracing.contrib.specialagent.rule.httpurlconnection;

import static org.junit.Assert.*;

import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.opentracing.contrib.specialagent.AgentRunner;
import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;
import io.opentracing.tag.Tags;

@RunWith(AgentRunner.class)
public class HttpURLConnectionTest {
  @Before
  public void before(final MockTracer tracer) {
    tracer.reset();
  }

  @Test
  public void succeedRequest(final MockTracer tracer) throws Exception {
    final URL url = new URL("http://www.google.com");
    final HttpURLConnection connection = (HttpURLConnection)url.openConnection();
    connection.setRequestMethod("GET");
    final int responseCode = connection.getResponseCode();
    assertEquals(200, responseCode);

    final List<MockSpan> spans = tracer.finishedSpans();
    assertEquals(1, spans.size());
    assertEquals(HttpURLConnectionAgentIntercept.COMPONENT_NAME, spans.get(0).tags().get(Tags.COMPONENT.getKey()));
  }

  @Test
  public void failedRequest(final MockTracer tracer) throws Exception {
    final URL url = new URL("http://localhost:12345");
    final HttpURLConnection connection = (HttpURLConnection)url.openConnection();
    connection.setRequestMethod("GET");
    try {
      final int responseCode = connection.getResponseCode();
      assertEquals(200, responseCode);
    }
    catch (final ConnectException ignore) {
    }

    final List<MockSpan> spans = tracer.finishedSpans();
    assertEquals(1, spans.size());
    assertEquals(HttpURLConnectionAgentIntercept.COMPONENT_NAME, spans.get(0).tags().get(Tags.COMPONENT.getKey()));
  }
}