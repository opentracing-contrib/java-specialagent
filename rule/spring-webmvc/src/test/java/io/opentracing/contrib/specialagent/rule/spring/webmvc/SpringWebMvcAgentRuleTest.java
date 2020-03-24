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

package io.opentracing.contrib.specialagent.rule.spring.webmvc;

import static org.junit.Assert.*;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.webapp.WebAppContext;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import io.opentracing.contrib.specialagent.AgentRule;
import io.opentracing.contrib.specialagent.AgentRunner;
import io.opentracing.contrib.specialagent.AgentRunner.Config;
import io.opentracing.contrib.specialagent.Level;

@RunWith(AgentRunner.class)
@Config(defer = true, isolateClassLoader = false, log = Level.FINE)
public class SpringWebMvcAgentRuleTest {
  public static Server startServer() throws Exception {
    final Server server = new Server(0);

    final WebAppContext webAppContext = new WebAppContext();
    webAppContext.setServer(server);
    webAppContext.setContextPath("/");
    webAppContext.setWar("src/test/webapp");

    server.setHandler(webAppContext);
    server.start();

    // jetty starts on random port
    return server;
  }

  @Test
  public void test() throws Exception {
    assertFalse(AgentRule.isEnabled(AgentRule.class.getName(), null));
    final Server server = startServer();
    final int port = ((ServerConnector)server.getConnectors()[0]).getLocalPort();
    final String url = "http://localhost:" + port;
    final ResponseEntity<String> responseEntity = new RestTemplate().getForEntity(url, String.class);
    assertEquals("test", responseEntity.getBody());
    assertTrue(AgentRule.isEnabled(AgentRule.class.getName(), null));
    server.stop();
    server.join();
  }
}