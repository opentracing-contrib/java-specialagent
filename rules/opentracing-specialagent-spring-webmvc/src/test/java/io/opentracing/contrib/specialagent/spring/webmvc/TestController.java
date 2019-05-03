package io.opentracing.contrib.specialagent.spring.webmvc;

import io.opentracing.util.GlobalTracer;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/")
public class TestController {

  @RequestMapping("/sync")
  public String test() {
    if(GlobalTracer.get().activeSpan() == null) {
      throw new RuntimeException("no active span");
    }
    return "sync";
  }

}
