package io.opentracing.contrib.agent.concurrent;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.Test;
import org.junit.runner.RunWith;

import io.opentracing.contrib.specialagent.AgentRunner;
import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;

/**
 * @author Pavol Loffay
 * @author Jose Montoya
 * @author Seva Safris
 */
@RunWith(AgentRunner.class)
@AgentRunner.Config(debug=true, verbose=true)
public class ExecutorServiceITest extends AbstractConcurrentTest {
	private static final int NUMBER_OF_THREADS = 4;

	@Test
	public void testExecuteRunnable(final MockTracer tracer) throws InterruptedException {
	  final CountDownLatch countDownLatch = new CountDownLatch(1);
	  final ExecutorService executorService = Executors.newFixedThreadPool(NUMBER_OF_THREADS);

	  final MockSpan parentSpan = tracer.buildSpan("foo").startManual();
		tracer.scopeManager().activate(parentSpan, true);
		executorService.execute(new TestRunnable(tracer, countDownLatch));

		countDownLatch.await();
		assertParentSpan(tracer, parentSpan);
		assertEquals(1, tracer.finishedSpans().size());
	}

	@Test
	public void testSubmitRunnable(final MockTracer tracer) throws InterruptedException {
	  final CountDownLatch countDownLatch = new CountDownLatch(1);
	  final ExecutorService executorService = Executors.newFixedThreadPool(NUMBER_OF_THREADS);

	  final MockSpan parentSpan = tracer.buildSpan("foo").startManual();
		tracer.scopeManager().activate(parentSpan, true);
		executorService.submit(new TestRunnable(tracer, countDownLatch));

		countDownLatch.await();
		assertParentSpan(tracer, parentSpan);
		assertEquals(1, tracer.finishedSpans().size());
	}

	@Test
	public void testSubmitRunnableTyped(final MockTracer tracer) throws InterruptedException {
	  final CountDownLatch countDownLatch = new CountDownLatch(1);
		final ExecutorService executorService = Executors.newFixedThreadPool(NUMBER_OF_THREADS);

		final MockSpan parentSpan = tracer.buildSpan("foo").startManual();
		tracer.scopeManager().activate(parentSpan, true);
		executorService.submit(new TestRunnable(tracer, countDownLatch), new Object());

		countDownLatch.await();
		assertParentSpan(tracer, parentSpan);
		assertEquals(1, tracer.finishedSpans().size());
	}

	@Test
	public void testSubmitCallable(final MockTracer tracer) throws InterruptedException {
	  final CountDownLatch countDownLatch = new CountDownLatch(1);
	  final ExecutorService executorService = Executors.newFixedThreadPool(NUMBER_OF_THREADS);

	  final MockSpan parentSpan = tracer.buildSpan("foo").startManual();
		tracer.scopeManager().activate(parentSpan, true);
		executorService.submit(new TestCallable(tracer, countDownLatch));

		countDownLatch.await();
		assertParentSpan(tracer, parentSpan);
		assertEquals(1, tracer.finishedSpans().size());
	}

	@Test
	public void testInvokeAll(final MockTracer tracer) throws InterruptedException {
	  final ExecutorService executorService = Executors.newFixedThreadPool(NUMBER_OF_THREADS);

	  final MockSpan parentSpan = tracer.buildSpan("foo").startManual();
		tracer.scopeManager().activate(parentSpan, true);
		final CountDownLatch countDownLatch = new CountDownLatch(2);
		executorService.invokeAll(Arrays.asList(new TestCallable(tracer, countDownLatch), new TestCallable(tracer, countDownLatch)));

		countDownLatch.await();
		assertParentSpan(tracer, parentSpan);
		assertEquals(2, tracer.finishedSpans().size());
	}

	@Test
	public void testInvokeAllTimeUnit(final MockTracer tracer) throws InterruptedException {
	  final ExecutorService executorService = Executors.newFixedThreadPool(NUMBER_OF_THREADS);

	  final MockSpan parentSpan = tracer.buildSpan("foo").startManual();
		tracer.scopeManager().activate(parentSpan, true);
		final CountDownLatch countDownLatch = new CountDownLatch(2);
		executorService.invokeAll(Arrays.asList(new TestCallable(tracer, countDownLatch), new TestCallable(tracer, countDownLatch)), 1, TimeUnit.SECONDS);

		countDownLatch.await();
		assertParentSpan(tracer, parentSpan);
		assertEquals(2, tracer.finishedSpans().size());
	}

	@Test
	public void testInvokeAnyTimeUnit(final MockTracer tracer) throws InterruptedException, ExecutionException, TimeoutException {
	  final CountDownLatch countDownLatch = new CountDownLatch(1);
	  final ExecutorService executorService = Executors.newFixedThreadPool(NUMBER_OF_THREADS);

	  final MockSpan parentSpan = tracer.buildSpan("foo").startManual();
		tracer.scopeManager().activate(parentSpan, true);
		executorService.invokeAny(Arrays.asList(new TestCallable(tracer, countDownLatch)), 1, TimeUnit.SECONDS);

		countDownLatch.await();
		assertParentSpan(tracer, parentSpan);
		assertEquals(1, tracer.finishedSpans().size());
	}

	@Test
	public void testInvokeAny(final MockTracer tracer) throws InterruptedException, ExecutionException {
	  final CountDownLatch countDownLatch = new CountDownLatch(1);
	  final ExecutorService executorService = Executors.newFixedThreadPool(NUMBER_OF_THREADS);

	  final MockSpan parentSpan = tracer.buildSpan("foo").startManual();
		tracer.scopeManager().activate(parentSpan, true);
		executorService.invokeAny(Arrays.asList(new TestCallable(tracer, countDownLatch)));

		countDownLatch.await();
		assertParentSpan(tracer, parentSpan);
		assertEquals(1, tracer.finishedSpans().size());
	}
}