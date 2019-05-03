package io.opentracing.contrib.specialagent.spring.webmvc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import io.opentracing.contrib.specialagent.AgentRunner;
import io.opentracing.contrib.specialagent.AgentRunner.Config;
import io.opentracing.mock.MockTracer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.webapp.WebAppContext;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.ResponseEntity;

@RunWith(AgentRunner.class)
@Config(isolateClassLoader = false)
public class SpringWebMvcTest {
  private static final String CONTEXT_PATH = "/tracing";
  private static Server jettyServer;
  // jetty starts on random port
  private static int serverPort;
  private static TestRestTemplate testRestTemplate;

  @BeforeClass
  public static void beforeClass() throws Exception {
    jettyServer = new Server(0);

    WebAppContext webApp = new WebAppContext();
    webApp.setServer(jettyServer);
    webApp.setContextPath(CONTEXT_PATH);
    webApp.setWar("src/test/webapp");

    jettyServer.setHandler(webApp);
    jettyServer.start();
    serverPort = ((ServerConnector) jettyServer.getConnectors()[0]).getLocalPort();

    testRestTemplate = new TestRestTemplate(new RestTemplateBuilder()
        .rootUri("http://localhost:" + serverPort + CONTEXT_PATH));
  }

  @AfterClass
  public static void afterTest() throws Exception {
    jettyServer.stop();
    jettyServer.join();
  }

  @Test
  public void test(MockTracer tracer) {
    ResponseEntity<String> responseEntity = testRestTemplate.getForEntity("/sync", String.class);
    assertEquals("sync", responseEntity.getBody());

    assertEquals(1, tracer.finishedSpans().size());
    assertFalse(tracer.finishedSpans().get(0).logEntries().isEmpty());
  }
}
