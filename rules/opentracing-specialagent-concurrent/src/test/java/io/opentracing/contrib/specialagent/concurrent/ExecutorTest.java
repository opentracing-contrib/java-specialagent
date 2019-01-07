package io.opentracing.contrib.specialagent.concurrent;

import static org.junit.Assert.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.junit.Test;
import org.junit.runner.RunWith;

import io.opentracing.contrib.specialagent.AgentRunner;
import io.opentracing.contrib.specialagent.Instrumenter;
import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;

/**
 * @author Pavol Loffay
 * @author Jose Montoya
 * @author Seva Safris
 */
@RunWith(AgentRunner.class)
@AgentRunner.Config(debug=true, verbose=true, instrumenter=Instrumenter.BYTEBUDDY)
public class ExecutorTest extends AbstractConcurrentTest {
  @Test
	public void testExecute(final MockTracer tracer) throws InterruptedException {
    final CountDownLatch countDownLatch = new CountDownLatch(1);
    final Executor executor = Executors.newFixedThreadPool(10);

    final MockSpan parentSpan = tracer.buildSpan("foo").startManual();
		tracer.scopeManager().activate(parentSpan, true);
		executor.execute(new TestRunnable(tracer, countDownLatch));

		countDownLatch.await();
		assertParentSpan(tracer, parentSpan);
		assertEquals(1, tracer.finishedSpans().size());
	}
}