package io.opentracing.contrib.specialagent.test.spring.webmvc;

import io.opentracing.contrib.specialagent.TestUtil;
import io.opentracing.util.GlobalTracer;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class HelloController {

  @RequestMapping(value = "/", method = RequestMethod.GET)
  @ResponseBody
  public String printWelcome(ModelMap model) {
    TestUtil.checkActiveSpan();
    return "hello";
  }

}
