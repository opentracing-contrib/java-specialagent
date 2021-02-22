package io.opentracing.contrib.specialagent.rule.quartz;


import io.opentracing.contrib.specialagent.AgentRunner;
import io.opentracing.contrib.specialagent.TestUtil;
import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;
import io.opentracing.tag.Tags;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(AgentRunner.class)
public class QuartzJobTest {

    @BeforeClass
    public static void beforeClass() throws Exception {
    }

    @Test
    public void test(final MockTracer tracer) {


        await().atMost(10, TimeUnit.SECONDS).until(TestUtil.reportedSpansSize(tracer), greaterThanOrEqualTo(1));
        final List<MockSpan> spans = tracer.finishedSpans();
        assertTrue(spans.size() >= 1);
        for (final MockSpan span : spans) {
            assertEquals("quartz-job", span.tags().get(Tags.COMPONENT.getKey()));
        }
    }

}
