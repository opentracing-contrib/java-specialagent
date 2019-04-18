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

import io.opentracing.contrib.specialagent.AgentRunner;
import io.opentracing.mock.MockTracer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author Pavol Loffay
 * @author Jose Montoya
 * @author Seva Safris
 */
@RunWith(AgentRunner.class)
public class ScheduledExecutorServiceTest extends AbstractConcurrentTest {
	private static final int NUMBER_OF_THREADS = 4;

	@Test
	public void scheduleRunnableTest(MockTracer tracer) throws InterruptedException {
		final ScheduledExecutorService executorService = Executors.newScheduledThreadPool(NUMBER_OF_THREADS);
		final CountDownLatch countDownLatch = new CountDownLatch(1);
		executorService.schedule(new TestRunnable(tracer, countDownLatch), 0, TimeUnit.MILLISECONDS);
		countDownLatch.await();
		executorService.shutdownNow();
		assertEquals(3, tracer.finishedSpans().size());
		assertParentSpan(tracer);
	}

	@Test
	public void scheduleCallableTest(final MockTracer tracer) throws InterruptedException {
		final ScheduledExecutorService executorService = Executors.newScheduledThreadPool(NUMBER_OF_THREADS);
		final CountDownLatch countDownLatch = new CountDownLatch(1);

		executorService.schedule(new TestCallable(tracer, countDownLatch), 0, TimeUnit.MILLISECONDS);
		countDownLatch.await();
		executorService.shutdownNow();
		assertEquals(3, tracer.finishedSpans().size());
		assertParentSpan(tracer);
	}

	@Test
	public void scheduleAtFixedRateTest(final MockTracer tracer) throws InterruptedException {
	  final ScheduledExecutorService executorService = Executors.newScheduledThreadPool(NUMBER_OF_THREADS);
    final CountDownLatch countDownLatch = new CountDownLatch(1);

		executorService.scheduleAtFixedRate(new TestRunnable(tracer, countDownLatch), 0, 10_000,
				TimeUnit.MILLISECONDS);
		countDownLatch.await();
		executorService.shutdownNow();
		assertEquals(3, tracer.finishedSpans().size());
		assertParentSpan(tracer);
	}

	@Test
	public void scheduleWithFixedDelayTest(final MockTracer tracer) throws InterruptedException {
	  final ScheduledExecutorService executorService = Executors.newScheduledThreadPool(NUMBER_OF_THREADS);
    final CountDownLatch countDownLatch = new CountDownLatch(1);

		executorService.scheduleWithFixedDelay(new TestRunnable(tracer, countDownLatch), 0, 10_000,
				TimeUnit.MILLISECONDS);
		countDownLatch.await();
		executorService.shutdownNow();
		assertEquals(3, tracer.finishedSpans().size());
		assertParentSpan(tracer);
	}
}