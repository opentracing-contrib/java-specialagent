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

package io.opentracing.contrib.specialagent.zuul;

import static org.junit.Assert.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.Response;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.opentracing.contrib.specialagent.AgentRunner;
import io.opentracing.contrib.specialagent.AgentRunner.Config;
import io.opentracing.mock.MockTracer;

@RunWith(AgentRunner.class)
@Config(isolateClassLoader = false)
public class JaxRsTest {
  @Test
  public void test(final MockTracer tracer) throws Exception {
    final Server server = new Server(0);
    server.setHandler(new AbstractHandler() {
      @Override
      public void handle(final String target, final Request baseRequest, final HttpServletRequest request, final HttpServletResponse response) {
        response.setStatus(HttpServletResponse.SC_OK);
        baseRequest.setHandled(true);
      }
    });
    server.start();

    final int port = ((ServerConnector)server.getConnectors()[0]).getLocalPort();
    final String url = "http://localhost:" + port;

    final Client client = ClientBuilder.newClient();
    final Response response = client.target(url).request().get();
    assertEquals(200, response.getStatus());

    assertEquals(1, tracer.finishedSpans().size());

    server.stop();
    server.join();
  }
}