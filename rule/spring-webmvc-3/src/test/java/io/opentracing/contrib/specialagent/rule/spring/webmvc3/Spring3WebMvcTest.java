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

package io.opentracing.contrib.specialagent.rule.spring.webmvc3;

import static org.awaitility.Awaitility.*;
import static org.hamcrest.core.IsEqual.*;
import static org.junit.Assert.*;

import java.util.concurrent.TimeUnit;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.webapp.WebAppContext;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import io.opentracing.contrib.specialagent.AgentRunner;
import io.opentracing.contrib.specialagent.AgentRunner.Config;
import io.opentracing.contrib.specialagent.TestUtil;
import io.opentracing.mock.MockTracer;

@RunWith(AgentRunner.class)
@Config(isolateClassLoader = false)
public class Spring3WebMvcTest {
  private static Server server;
  private static String url;

  @BeforeClass
  public static void beforeClass() throws Exception {
    server = new Server(0);

    final WebAppContext webAppContext = new WebAppContext();
    webAppContext.setServer(server);
    webAppContext.setContextPath("/");
    webAppContext.setWar("src/test/webapp");

    server.setHandler(webAppContext);
    server.start();

    // jetty starts on random port
    final int port = ((ServerConnector)server.getConnectors()[0]).getLocalPort();
    url = "http://localhost:" + port;
  }

  @AfterClass
  public static void afterClass() throws Exception {
    server.stop();
    server.join();
  }

  @Test
  public void test(final MockTracer tracer) {
    final ResponseEntity<String> responseEntity = new RestTemplate().getForEntity(url, String.class);
    assertEquals("test", responseEntity.getBody());

    await().atMost(15, TimeUnit.SECONDS).until(TestUtil.reportedSpansSize(tracer), equalTo(1));

    assertEquals(1, tracer.finishedSpans().size());
    assertFalse(tracer.finishedSpans().get(0).logEntries().isEmpty());
  }
}