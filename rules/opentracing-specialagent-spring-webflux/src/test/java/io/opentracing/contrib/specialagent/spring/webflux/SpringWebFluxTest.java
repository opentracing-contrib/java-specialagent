package io.opentracing.contrib.specialagent.spring.webflux;

import io.opentracing.contrib.specialagent.AgentRunner;
import io.opentracing.contrib.specialagent.AgentRunner.Config;
import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;
import io.opentracing.tag.Tags;
import java.net.URI;
import java.util.List;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.boot.web.embedded.jetty.JettyReactiveWebServerFactory;
import org.springframework.boot.web.reactive.context.ReactiveWebServerApplicationContext;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.HttpHandler;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebHandler;
import org.springframework.web.server.adapter.WebHttpHandlerBuilder;
import reactor.core.publisher.Mono;

@RunWith(AgentRunner.class)
@Config(isolateClassLoader = false)
public class SpringWebFluxTest {
  private static final ReactiveWebServerApplicationContext APPLICATION_CONTEXT = new ReactiveWebServerApplicationContext();
  private static TestRestTemplate testRestTemplate;

  @Before
  public void before(MockTracer tracer) {
    tracer.reset();
  }

  @BeforeClass
  public static void beforeClass() {
    APPLICATION_CONTEXT
        .registerBean("jettyReactiveWebServerFactory", JettyReactiveWebServerFactory.class, () ->
            new JettyReactiveWebServerFactory(0));
    APPLICATION_CONTEXT.registerBean("httpHandler", HttpHandler.class, () ->
        WebHttpHandlerBuilder.applicationContext(APPLICATION_CONTEXT).build());

    APPLICATION_CONTEXT.registerBean("webHandler", WebHandler.class, () ->
        SpringWebFluxTest::handler);
    APPLICATION_CONTEXT.refresh();
    int serverPort = APPLICATION_CONTEXT.getWebServer().getPort();
    testRestTemplate = new TestRestTemplate(new RestTemplateBuilder()
        .rootUri("http://127.0.0.1:" + serverPort));
  }

  private static Mono<Void> handler(final ServerWebExchange serverWebExchange) {
    final ServerHttpResponse response = serverWebExchange.getResponse();
    response.setStatusCode(HttpStatus.OK);
    return response
        .writeWith(Mono.just(new DefaultDataBufferFactory().wrap("Hello World!\n".getBytes())));
  }

  @AfterClass
  public static void afterClass() {
    APPLICATION_CONTEXT.close();
  }

  @Test
  public void testClient(MockTracer tracer) {
    WebClient client = WebClient.builder().build();
    client.get()
        .uri(URI.create("http://example.com"))
        .retrieve()
        .bodyToMono(String.class)
        .block();

    Assert.assertEquals(1, tracer.finishedSpans().size());
  }

  @Test
  public void testGet(MockTracer tracer) {
    testRestTemplate.getForEntity("/", String.class);

    final List<MockSpan> mockSpans = tracer.finishedSpans();
    Assert.assertEquals(1, mockSpans.size());

    final MockSpan span = mockSpans.get(0);
    Assert.assertEquals("GET", span.operationName());

    Assert.assertEquals(8, span.tags().size());
    Assert.assertEquals(Tags.SPAN_KIND_SERVER, span.tags().get(Tags.SPAN_KIND.getKey()));
    Assert.assertEquals("GET", span.tags().get(Tags.HTTP_METHOD.getKey()));
    Assert
        .assertEquals(testRestTemplate.getRootUri() + "/", span.tags().get(Tags.HTTP_URL.getKey()));
    Assert.assertEquals(200, span.tags().get(Tags.HTTP_STATUS.getKey()));
    Assert.assertEquals("java-spring-webflux", span.tags().get(Tags.COMPONENT.getKey()));
    Assert.assertNotNull(span.tags().get(Tags.PEER_PORT.getKey()));
    Assert.assertEquals("127.0.0.1", span.tags().get(Tags.PEER_HOST_IPV4.getKey()));
  }

}
