package io.opentracing.contrib.specialagent.rule.mule4;

import io.opentracing.contrib.specialagent.AgentRunner;
import io.opentracing.mock.MockTracer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mule.tck.junit4.AbstractMuleTestCase;

@AgentRunner.Config(isolateClassLoader = false)
@RunWith(AgentRunner.class)
public class CoreTest extends AbstractMuleTestCase {

    @Before
    public void before(final MockTracer tracer) throws Exception {
        // clear traces
        tracer.reset();

    }

    @After
    public void after() throws Exception {
    }

    @Test
    public void test(final MockTracer tracer) throws Exception {
        // TODO: 1/10/20 Figure out how to make MuleArtifactFunctionalTestCase work with AgentRunner
        // until then the mule-4.x/container-itest will be our only coverage
    }
}
