package io.opentracing.contrib.specialagent.test.spring.webflux;

import io.opentracing.contrib.specialagent.TestUtil;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
public class HelloController {

  @RequestMapping("/")
  public Mono<String> index() {
    TestUtil.checkActiveSpan();
    return Mono.just("WebFlux");
  }

}
