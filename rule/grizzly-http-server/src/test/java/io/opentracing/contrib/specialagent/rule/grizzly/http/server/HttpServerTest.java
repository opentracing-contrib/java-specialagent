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

package io.opentracing.contrib.specialagent.rule.grizzly.http.server;

import static org.glassfish.grizzly.http.server.NetworkListener.DEFAULT_NETWORK_HOST;
import static org.junit.Assert.*;

import java.io.IOException;
import java.util.List;

import org.glassfish.grizzly.http.server.HttpHandler;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.grizzly.http.server.NetworkListener;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.grizzly.http.server.Response;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.opentracing.contrib.specialagent.AgentRunner;
import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;

/**
 * @author Jose Montoya
 */
@RunWith(AgentRunner.class)
public class HttpServerTest {
  protected static final int PORT = 18906;
  protected static final String LOCALHOST = "localhost";
  private HttpServer httpServer;

  @Before
  public void before(final MockTracer tracer) throws IOException {
    // clear traces
    tracer.reset();

    httpServer = new HttpServer();
    NetworkListener listener = new NetworkListener("grizzly", DEFAULT_NETWORK_HOST, PORT);
    httpServer.addListener(listener);
    httpServer.start();
  }

  @After
  public void after() throws Exception {
    if (httpServer != null) {
      httpServer.shutdownNow();
    }
  }

  @Test
  public void testSyncResponse(final MockTracer tracer) throws Exception {
    httpServer.getServerConfiguration().addHttpHandler(new HttpHandler() {
      @Override
      public void service(final Request request, final Response response) throws Exception {
        response.setStatus(201);
      }
    });

    final org.apache.http.client.fluent.Response response = org.apache.http.client.fluent.Request.Get("http://" + LOCALHOST + ":" + PORT + "/").addHeader("beep", "boop").execute();
    assertEquals(201, response.returnResponse().getStatusLine().getStatusCode());

    final List<MockSpan> spans = tracer.finishedSpans();
    assertEquals(1, spans.size());
  }
}