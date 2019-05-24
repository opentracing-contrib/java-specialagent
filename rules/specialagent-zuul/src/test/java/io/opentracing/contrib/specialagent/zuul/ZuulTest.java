package io.opentracing.contrib.specialagent.zuul;

import static junit.framework.TestCase.assertTrue;

import com.netflix.zuul.FilterLoader;
import com.netflix.zuul.ZuulFilter;
import io.opentracing.contrib.specialagent.AgentRunner;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AgentRunner.class)
public class ZuulTest {

  @Test
  public void test() {
    ZuulFilter preFilter = FilterLoader.getInstance().getFiltersByType("pre").get(0);
    assertTrue(preFilter instanceof TracePreZuulFilter);

    ZuulFilter postFilter = FilterLoader.getInstance().getFiltersByType("post").get(0);
    assertTrue(postFilter instanceof TracePostZuulFilter);
  }
}
