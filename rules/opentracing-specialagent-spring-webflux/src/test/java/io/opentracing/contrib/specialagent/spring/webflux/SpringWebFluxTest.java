package io.opentracing.contrib.specialagent.spring.webflux;

import io.opentracing.contrib.specialagent.AgentRunner;
import io.opentracing.contrib.specialagent.AgentRunner.Config;
import io.opentracing.mock.MockTracer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;

@RunWith(AgentRunner.class)
@Config(isolateClassLoader = false)
public class SpringWebFluxTest {

  @Test
  public void test(MockTracer tracer) {

    final WebClient client = WebClient.builder().baseUrl("http://www.google.com")
        .build();
    final ClientResponse response = client.get().exchange().block();

    System.out.println(response.statusCode());

    System.out.println(tracer.finishedSpans());

  }
}
