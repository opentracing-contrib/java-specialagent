package io.opentracing.contrib.specialagent.test.spring.webmvc;

import io.opentracing.contrib.specialagent.TestUtil;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class HelloController {

  @RequestMapping(value = "/", method = RequestMethod.GET)
  @ResponseBody
  public String index() {
    TestUtil.checkActiveSpan();
    return "index";
  }

}
