package io.opentracing.contrib.specialagent.rule.dubbo26;

import io.opentracing.contrib.specialagent.AgentRunner;
import io.opentracing.mock.MockTracer;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AgentRunner.class)
public class DubboAgentTest {

    @Test
    public void falsehoodTest(final MockTracer tracer) throws Exception {
        //TODO change dubboTest to DubboAgent, there is a problem that fail to start dubbo with AgentRunner.
    }
}
