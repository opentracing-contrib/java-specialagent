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
import io.opentracing.Span;
import io.opentracing.contrib.specialagent.AgentRunner;
import io.opentracing.mock.MockTracer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
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
  private ScheduledExecutorService executorService;

  @Before
  public void before() {
    executorService = Executors.newScheduledThreadPool(NUMBER_OF_THREADS);
  }

  @After
  public void after() {
    if (executorService != null) {
      executorService.shutdownNow();
    }
  }

  @Test
  public void scheduleRunnableTestVerbose(MockTracer tracer) throws InterruptedException {
    System.setProperty(ConcurrentAgentMode.CONCURRENT_VERBOSE_MODE, "true");
    final CountDownLatch countDownLatch = new CountDownLatch(1);
    executorService.schedule(new TestRunnable(tracer, countDownLatch), 0, TimeUnit.MILLISECONDS);
    countDownLatch.await();
    executorService.shutdownNow();
    assertEquals(3, tracer.finishedSpans().size());
    assertParentSpan(tracer);
  }

  @Test
  public void scheduleRunnableTestSilent(MockTracer tracer) throws InterruptedException {
    System.clearProperty(ConcurrentAgentMode.CONCURRENT_VERBOSE_MODE);
    final CountDownLatch countDownLatch = new CountDownLatch(1);
    executorService.schedule(new TestRunnable(tracer, countDownLatch), 0, TimeUnit.MILLISECONDS);
    countDownLatch.await();
    executorService.shutdownNow();
    assertEquals(1, tracer.finishedSpans().size());
    assertParentSpan(tracer);
  }

  @Test
  public void scheduleRunnableTestSilentWithParent(MockTracer tracer) throws InterruptedException {
    System.clearProperty(ConcurrentAgentMode.CONCURRENT_VERBOSE_MODE);
    final CountDownLatch countDownLatch = new CountDownLatch(1);
    Span parent = tracer.buildSpan("parent").start();
    try(Scope scope = tracer.activateSpan(parent)) {
      executorService.schedule(new TestRunnable(tracer, countDownLatch), 0, TimeUnit.MILLISECONDS);
    }
    countDownLatch.await();
    parent.finish();
    executorService.shutdownNow();
    assertEquals(2, tracer.finishedSpans().size());
    assertParentSpan(tracer);
  }

  @Test
  public void scheduleCallableTestVerbose(final MockTracer tracer) throws InterruptedException {
    System.setProperty(ConcurrentAgentMode.CONCURRENT_VERBOSE_MODE, "true");
    final CountDownLatch countDownLatch = new CountDownLatch(1);

    executorService.schedule(new TestCallable(tracer, countDownLatch), 0, TimeUnit.MILLISECONDS);
    countDownLatch.await();
    executorService.shutdownNow();
    assertEquals(3, tracer.finishedSpans().size());
    assertParentSpan(tracer);
  }

  @Test
  public void scheduleCallableTestSilent(final MockTracer tracer) throws InterruptedException {
    System.clearProperty(ConcurrentAgentMode.CONCURRENT_VERBOSE_MODE);
    final CountDownLatch countDownLatch = new CountDownLatch(1);

    executorService.schedule(new TestCallable(tracer, countDownLatch), 0, TimeUnit.MILLISECONDS);
    countDownLatch.await();
    executorService.shutdownNow();
    assertEquals(1, tracer.finishedSpans().size());
    assertParentSpan(tracer);
  }

  @Test
  public void scheduleCallableTestSilentWithParent(final MockTracer tracer) throws InterruptedException {
    System.clearProperty(ConcurrentAgentMode.CONCURRENT_VERBOSE_MODE);
    final CountDownLatch countDownLatch = new CountDownLatch(1);

    Span parent = tracer.buildSpan("parent").start();
    try(Scope scope = tracer.activateSpan(parent)) {
      executorService.schedule(new TestCallable(tracer, countDownLatch), 0, TimeUnit.MILLISECONDS);
    }
    countDownLatch.await();
    parent.finish();
    executorService.shutdownNow();
    assertEquals(2, tracer.finishedSpans().size());
    assertParentSpan(tracer);
  }

  @Test
  public void scheduleAtFixedRateTestVerbose(final MockTracer tracer) throws InterruptedException {
    System.setProperty(ConcurrentAgentMode.CONCURRENT_VERBOSE_MODE, "true");
    final CountDownLatch countDownLatch = new CountDownLatch(1);

    executorService.scheduleAtFixedRate(new TestRunnable(tracer, countDownLatch), 0, 10_000,
        TimeUnit.MILLISECONDS);
    countDownLatch.await();
    executorService.shutdownNow();
    assertEquals(3, tracer.finishedSpans().size());
    assertParentSpan(tracer);
  }

  @Test
  public void scheduleAtFixedRateTestSilent(final MockTracer tracer) throws InterruptedException {
    System.clearProperty(ConcurrentAgentMode.CONCURRENT_VERBOSE_MODE);
    final CountDownLatch countDownLatch = new CountDownLatch(1);

    executorService.scheduleAtFixedRate(new TestRunnable(tracer, countDownLatch), 0, 10_000,
        TimeUnit.MILLISECONDS);
    countDownLatch.await();
    executorService.shutdownNow();
    assertEquals(1, tracer.finishedSpans().size());
    assertParentSpan(tracer);
  }

  @Test
  public void scheduleAtFixedRateTestSilentWithParent(final MockTracer tracer) throws InterruptedException {
    System.clearProperty(ConcurrentAgentMode.CONCURRENT_VERBOSE_MODE);
    final CountDownLatch countDownLatch = new CountDownLatch(1);

    Span parent = tracer.buildSpan("parent").start();
    try(Scope scope = tracer.activateSpan(parent)) {
      executorService.scheduleAtFixedRate(new TestRunnable(tracer, countDownLatch), 0, 10_000,
          TimeUnit.MILLISECONDS);
    }
    countDownLatch.await();
    parent.finish();
    executorService.shutdownNow();
    assertEquals(2, tracer.finishedSpans().size());
    assertParentSpan(tracer);
  }

  @Test
  public void scheduleWithFixedDelayTestVerbose(final MockTracer tracer) throws InterruptedException {
    System.setProperty(ConcurrentAgentMode.CONCURRENT_VERBOSE_MODE, "true");
    final CountDownLatch countDownLatch = new CountDownLatch(1);

    executorService.scheduleWithFixedDelay(new TestRunnable(tracer, countDownLatch), 0, 10_000,
        TimeUnit.MILLISECONDS);
    countDownLatch.await();
    executorService.shutdownNow();
    assertEquals(3, tracer.finishedSpans().size());
    assertParentSpan(tracer);
  }

  @Test
  public void scheduleWithFixedDelayTestSilent(final MockTracer tracer) throws InterruptedException {
    System.clearProperty(ConcurrentAgentMode.CONCURRENT_VERBOSE_MODE);
    final CountDownLatch countDownLatch = new CountDownLatch(1);

    executorService.scheduleWithFixedDelay(new TestRunnable(tracer, countDownLatch), 0, 10_000,
        TimeUnit.MILLISECONDS);
    countDownLatch.await();
    executorService.shutdownNow();
    assertEquals(1, tracer.finishedSpans().size());
    assertParentSpan(tracer);
  }

  @Test
  public void scheduleWithFixedDelayTestSilentWithParent(final MockTracer tracer) throws InterruptedException {
    System.clearProperty(ConcurrentAgentMode.CONCURRENT_VERBOSE_MODE);
    final CountDownLatch countDownLatch = new CountDownLatch(1);

    Span parent = tracer.buildSpan("parent").start();
    try(Scope scope = tracer.activateSpan(parent)) {
      executorService.scheduleWithFixedDelay(new TestRunnable(tracer, countDownLatch), 0, 10_000,
          TimeUnit.MILLISECONDS);
    }
    countDownLatch.await();
    parent.finish();
    executorService.shutdownNow();
    assertEquals(2, tracer.finishedSpans().size());
    assertParentSpan(tracer);
  }
}