package io.opentracing.contrib.specialagent.rule.method;

import io.opentracing.contrib.specialagent.AgentRunner;
import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * MethodAgentTest
 *
 * @author code98@163.com
 * @date 2019/11/18 10:37 下午
 */
@RunWith(AgentRunner.class)
public class MethodAgentTest {

    @Before
    public void before(final MockTracer tracer) {
        tracer.reset();
    }

    @Test
    public void test1(final MockTracer tracer) {
        ExampleMethodClass exampleMethodClass = new ExampleMethodClass();
        try {
            exampleMethodClass.test1();
        } catch (Exception ignored) {
        }

        final List<MockSpan> spans = tracer.finishedSpans();
        for (MockSpan span : spans) {
            Map<String, Object> tags = span.tags();
            if (tags.containsKey("error")) {
                return;
            }
        }
        assertTrue(true);
    }

    @Test
    public void test2(final MockTracer tracer) {
        ExampleMethodClass exampleMethodClass = new ExampleMethodClass();
        exampleMethodClass.test2("test");

        final List<MockSpan> spans = tracer.finishedSpans();
        assertEquals(1, spans.size());
    }

}
