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

import static org.junit.Assert.assertEquals;

import io.opentracing.Scope;
import io.opentracing.contrib.specialagent.AgentRunner;
import io.opentracing.mock.MockTracer;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author Pavol Loffay
 * @author Jose Montoya
 * @author Seva Safris
 */
@RunWith(AgentRunner.class)
public class ExecutorServiceTest extends AbstractConcurrentTest {
	private static final int NUMBER_OF_THREADS = 4;

	@Test
	public void testExecuteRunnable(final MockTracer tracer) throws InterruptedException {
	  final CountDownLatch countDownLatch = new CountDownLatch(1);
	  final ExecutorService executorService = Executors.newFixedThreadPool(NUMBER_OF_THREADS);

		executorService.execute(new TestRunnable(tracer, countDownLatch));

		countDownLatch.await();
		assertEquals(3, tracer.finishedSpans().size());
	}

	@Test
	public void testSubmitRunnable(final MockTracer tracer) throws InterruptedException {
	  final CountDownLatch countDownLatch = new CountDownLatch(1);
	  final ExecutorService executorService = Executors.newFixedThreadPool(NUMBER_OF_THREADS);

  	executorService.submit(new TestRunnable(tracer, countDownLatch));

		countDownLatch.await();
		assertEquals(3, tracer.finishedSpans().size());
		assertParentSpan(tracer);
	}

	@Test
	public void testSubmitRunnableTyped(final MockTracer tracer) throws InterruptedException {
	  final CountDownLatch countDownLatch = new CountDownLatch(1);
		final ExecutorService executorService = Executors.newFixedThreadPool(NUMBER_OF_THREADS);

		executorService.submit(new TestRunnable(tracer, countDownLatch), new Object());

		countDownLatch.await();
		assertEquals(3, tracer.finishedSpans().size());
		assertParentSpan(tracer);
	}

	@Test
	public void testSubmitCallable(final MockTracer tracer) throws InterruptedException {
	  final CountDownLatch countDownLatch = new CountDownLatch(1);
	  final ExecutorService executorService = Executors.newFixedThreadPool(NUMBER_OF_THREADS);

		executorService.submit(new TestCallable(tracer, countDownLatch));

		countDownLatch.await();
		assertEquals(3, tracer.finishedSpans().size());
		assertParentSpan(tracer);
	}

	@Test
	public void testInvokeAll(final MockTracer tracer) throws InterruptedException {
	  final ExecutorService executorService = Executors.newFixedThreadPool(NUMBER_OF_THREADS);
		final CountDownLatch countDownLatch = new CountDownLatch(2);

		try (Scope scope = tracer.buildSpan("parent").startActive(true)) {
			executorService.invokeAll(Arrays.asList(new TestCallable(tracer, countDownLatch),
					new TestCallable(tracer, countDownLatch)));
		}

		countDownLatch.await();
		assertEquals(7, tracer.finishedSpans().size());
		assertParentSpan(tracer);
	}

	@Test
	public void testInvokeAllTimeUnit(final MockTracer tracer) throws InterruptedException {
	  final ExecutorService executorService = Executors.newFixedThreadPool(NUMBER_OF_THREADS);
		final CountDownLatch countDownLatch = new CountDownLatch(2);

		try (Scope scope = tracer.buildSpan("parent").startActive(true)) {
			executorService.invokeAll(Arrays.asList(new TestCallable(tracer, countDownLatch),
					new TestCallable(tracer, countDownLatch)), 1, TimeUnit.SECONDS);
		}

		countDownLatch.await();
		assertEquals(7, tracer.finishedSpans().size());
		assertParentSpan(tracer);
	}

	@Test
	public void testInvokeAnyTimeUnit(final MockTracer tracer) throws InterruptedException, ExecutionException, TimeoutException {
	  final CountDownLatch countDownLatch = new CountDownLatch(1);
	  final ExecutorService executorService = Executors.newFixedThreadPool(NUMBER_OF_THREADS);

		executorService
					.invokeAny(Arrays.asList(new TestCallable(tracer, countDownLatch)), 1, TimeUnit.SECONDS);

		countDownLatch.await();
		assertEquals(3, tracer.finishedSpans().size());
		assertParentSpan(tracer);
	}

	@Test
	public void testInvokeAny(final MockTracer tracer) throws InterruptedException, ExecutionException {
	  final CountDownLatch countDownLatch = new CountDownLatch(1);
	  final ExecutorService executorService = Executors.newFixedThreadPool(NUMBER_OF_THREADS);

		executorService.invokeAny(Arrays.asList(new TestCallable(tracer, countDownLatch)));

		countDownLatch.await();
		assertEquals(3, tracer.finishedSpans().size());
		assertParentSpan(tracer);
	}
}