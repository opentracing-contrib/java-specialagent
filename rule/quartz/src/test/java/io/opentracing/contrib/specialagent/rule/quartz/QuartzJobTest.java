package io.opentracing.contrib.specialagent.rule.quartz;


import io.opentracing.contrib.specialagent.AgentRunner;
import io.opentracing.contrib.specialagent.TestUtil;
import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;
import io.opentracing.tag.Tags;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.quartz.*;
import org.quartz.impl.StdSchedulerFactory;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.quartz.JobBuilder.newJob;
import static org.quartz.TriggerBuilder.newTrigger;

@RunWith(AgentRunner.class)
public class QuartzJobTest {

    static Scheduler scheduler = null;

    @BeforeClass
    public static void beforeClass() throws Exception {
        scheduler = StdSchedulerFactory.getDefaultScheduler();
        scheduler.start();
    }

    @Test
    public void test(final MockTracer tracer) throws SchedulerException {
        JobDetail job = newJob(TestJob.class)
                .withIdentity("job1", "group1")
                .build();
        Trigger trigger = newTrigger()
                .withIdentity("trigger1", "group1")
                .startNow()
                .build();
        scheduler.scheduleJob(job, trigger);
        await().atMost(10, TimeUnit.SECONDS).until(TestUtil.reportedSpansSize(tracer), greaterThanOrEqualTo(1));
        final List<MockSpan> spans = tracer.finishedSpans();
        assertTrue(spans.size() >= 1);
        for (final MockSpan span : spans) {
            assertEquals("quartz-job", span.tags().get(Tags.COMPONENT.getKey()));
        }
    }

    @AfterClass
    public static void afterClass() throws Exception {
        scheduler.start();
    }

    public static class TestJob implements Job {
        @Override
        public void execute(JobExecutionContext jobExecutionContext) throws JobExecutionException {
            System.out.println("test job.");
        }
    }

}
