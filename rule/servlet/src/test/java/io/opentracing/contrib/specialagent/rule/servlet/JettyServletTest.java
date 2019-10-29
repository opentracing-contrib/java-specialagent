/*
 * Copyright 2017 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentracing.contrib.specialagent.rule.servlet;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.EnumSet;
import java.util.List;

import javax.servlet.DispatcherType;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletHandler;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.opentracing.contrib.specialagent.AgentRunner;
import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * @author gbrown
 * @author Seva Safris
 */
@RunWith(AgentRunner.class)
@AgentRunner.Config(disable = "okhttp")
public class JettyServletTest {
  // jetty starts on random port
  private static int serverPort;
  private static Server server;

  @BeforeClass
  public static void beforeClass() throws Exception {
    server = new Server(0);

    final ServletHandler servletHandler = new ServletHandler();
    servletHandler.addServletWithMapping(MockServlet.class, "/hello");
    servletHandler.addFilterWithMapping(MockFilter.class, "/*", EnumSet.of(DispatcherType.REQUEST));
    server.setHandler(servletHandler);

    server.start();
    serverPort = ((ServerConnector)server.getConnectors()[0]).getLocalPort();
  }

  @Test
  public void testHelloRequest(final MockTracer tracer) throws IOException {
    MockFilter.count = 0;
    MockServlet.count = 0;

    final OkHttpClient client = new OkHttpClient();
    final Request request = new Request.Builder().url("http://localhost:" + serverPort + "/hello").build();
    final Response response = client.newCall(request).execute();

    assertEquals("MockServlet response", HttpServletResponse.SC_ACCEPTED, response.code());
    assertEquals("MockServlet count", 1, MockServlet.count);
    assertEquals("MockFilter count", 1, MockFilter.count);

    final List<MockSpan> spans = tracer.finishedSpans();
    assertEquals("MockTracer spans: " + spans, 1, spans.size());
  }

  @AfterClass
  public static void afterClass() throws Exception {
    server.stop();
    server.join();
  }
}