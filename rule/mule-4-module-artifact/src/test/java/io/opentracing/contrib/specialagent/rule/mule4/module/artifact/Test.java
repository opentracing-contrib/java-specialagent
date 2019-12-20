package io.opentracing.contrib.specialagent.rule.mule4.module.artifact;

import io.opentracing.contrib.specialagent.AgentRunner;
import io.opentracing.mock.MockTracer;
import org.junit.After;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.mule.tck.junit4.AbstractMuleTestCase;

@RunWith(AgentRunner.class)
public class Test extends AbstractMuleTestCase {

    @Before
    public void before(final MockTracer tracer) throws Exception {
        // clear traces
        tracer.reset();

    }

    @After
    public void after() throws Exception {
    }

    @org.junit.Test
    public void httpServiceTest(final MockTracer tracer) throws Exception {
    }
}
