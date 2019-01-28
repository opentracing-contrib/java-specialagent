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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.junit.Test;
import org.junit.runner.RunWith;

import io.opentracing.Scope;
import io.opentracing.contrib.specialagent.AgentRunner;
import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;

/**
 * @author Pavol Loffay
 * @author Jose Montoya
 * @author Seva Safris
 */
@RunWith(AgentRunner.class)
@AgentRunner.Config(verbose=true)
public class ScheduledExecutorServiceTest extends AbstractConcurrentTest {
	private static final int NUMBER_OF_THREADS = 4;

	@Test
	public void scheduleRunnableTest(final MockTracer tracer) throws InterruptedException {
	  final ScheduledExecutorService executorService = Executors.newScheduledThreadPool(NUMBER_OF_THREADS);
    final CountDownLatch countDownLatch = new CountDownLatch(1);

	  final MockSpan parentSpan = tracer.buildSpan("foo-1").start();
		try (final Scope scope = tracer.scopeManager().activate(parentSpan, true)) {
			executorService.schedule(new TestRunnable(tracer, countDownLatch), 300, TimeUnit.MILLISECONDS);
			countDownLatch.await();
			assertParentSpan(tracer, parentSpan);
			assertEquals(1, tracer.finishedSpans().size());
		}
	}

	@Test
	public void scheduleCallableTest(final MockTracer tracer) throws InterruptedException {
	  final ScheduledExecutorService executorService = Executors.newScheduledThreadPool(NUMBER_OF_THREADS);
    final CountDownLatch countDownLatch = new CountDownLatch(1);

		final MockSpan parentSpan = tracer.buildSpan("foo-2").start();
		try (final Scope scope = tracer.scopeManager().activate(parentSpan, true)) {
			executorService.schedule(new TestCallable(tracer, countDownLatch), 300, TimeUnit.MILLISECONDS);
			countDownLatch.await();
			assertParentSpan(tracer, parentSpan);
			assertEquals(1, tracer.finishedSpans().size());
		}
	}

	@Test
	public void scheduleAtFixedRateTest(final MockTracer tracer) throws InterruptedException {
	  final ScheduledExecutorService executorService = Executors.newScheduledThreadPool(NUMBER_OF_THREADS);
    final CountDownLatch countDownLatch = new CountDownLatch(2);

		final MockSpan parentSpan = tracer.buildSpan("foo-3").start();
		try (final Scope scope = tracer.scopeManager().activate(parentSpan, true)) {
			executorService.scheduleAtFixedRate(new TestRunnable(tracer, countDownLatch), 0, 300, TimeUnit.MILLISECONDS);
			countDownLatch.await();
			executorService.shutdown();
			assertParentSpan(tracer, parentSpan);
			assertEquals(2, tracer.finishedSpans().size());
		}
	}

	@Test
	public void scheduleWithFixedDelayTest(final MockTracer tracer) throws InterruptedException {
	  final ScheduledExecutorService executorService = Executors.newScheduledThreadPool(NUMBER_OF_THREADS);
    final CountDownLatch countDownLatch = new CountDownLatch(2);

	  final MockSpan parentSpan = tracer.buildSpan("foo-4").start();
		try (final Scope scope = tracer.scopeManager().activate(parentSpan, true)) {
			executorService.scheduleWithFixedDelay(new TestRunnable(tracer, countDownLatch), 0, 300, TimeUnit.MILLISECONDS);
			countDownLatch.await();
			executorService.shutdown();
			assertParentSpan(tracer, parentSpan);
			assertEquals(2, tracer.finishedSpans().size());
		}
	}
}