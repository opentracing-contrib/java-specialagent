/* Copyright 2018 The OpenTracing Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentracing.contrib.specialagent.concurrent;

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
import io.opentracing.contrib.specialagent.Manager.Event;
import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;

/**
 * @author Pavol Loffay
 * @author Jose Montoya
 * @author Seva Safris
 */
@RunWith(AgentRunner.class)
@AgentRunner.Config(events=Event.ERROR)
public class ExecutorServiceTest extends AbstractConcurrentTest {
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