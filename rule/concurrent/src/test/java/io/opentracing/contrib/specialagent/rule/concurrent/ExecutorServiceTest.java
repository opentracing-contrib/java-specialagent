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

package io.opentracing.contrib.specialagent.rule.concurrent;

import static org.junit.Assert.assertFalse;

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
public class ExecutorServiceTest extends AbstractConcurrentTest {
  private static final int NUMBER_OF_THREADS = 4;
  private ExecutorService executorService;

  @Before
  public void before() {
    executorService = Executors.newFixedThreadPool(NUMBER_OF_THREADS);
  }

  @After
  public void after() {
    if (executorService != null) {
      executorService.shutdownNow();
    }
  }

  @Test
  @AgentRunner.TestConfig(verbose=true)
  public void testExecuteRunnableVerbose(final MockTracer tracer) throws InterruptedException {
    final CountDownLatch countDownLatch = new CountDownLatch(1);

    executorService.execute(new TestRunnable(tracer, countDownLatch));

    countDownLatch.await();
    assertFalse(tracer.finishedSpans().isEmpty());
  }

  @Test
  @AgentRunner.TestConfig(verbose=false)
  public void testExecuteRunnableSilent(final MockTracer tracer) throws InterruptedException {
    final CountDownLatch countDownLatch = new CountDownLatch(1);

    executorService.execute(new TestRunnable(tracer, countDownLatch));

    countDownLatch.await();
    assertFalse(tracer.finishedSpans().isEmpty());
  }

  @Test
  @AgentRunner.TestConfig(verbose=false)
  public void testExecuteRunnableSilentWithParent(final MockTracer tracer) throws InterruptedException {
    final CountDownLatch countDownLatch = new CountDownLatch(1);

    try (final Scope scope = tracer.buildSpan("parent").startActive(true)) {
      executorService.execute(new TestRunnable(tracer, countDownLatch));
    }

    countDownLatch.await();
    assertFalse(tracer.finishedSpans().isEmpty());
  }

  @Test
  @AgentRunner.TestConfig(verbose=true)
  public void testSubmitRunnableVerbose(final MockTracer tracer) throws InterruptedException {
    final CountDownLatch countDownLatch = new CountDownLatch(1);

    executorService.submit(new TestRunnable(tracer, countDownLatch));

    countDownLatch.await();
    assertFalse(tracer.finishedSpans().isEmpty());
  }

  @Test
  @AgentRunner.TestConfig(verbose=false)
  public void testSubmitRunnableSilent(final MockTracer tracer) throws InterruptedException {
    final CountDownLatch countDownLatch = new CountDownLatch(1);

    executorService.submit(new TestRunnable(tracer, countDownLatch));

    countDownLatch.await();
    assertFalse(tracer.finishedSpans().isEmpty());
  }

  @Test
  @AgentRunner.TestConfig(verbose=false)
  public void testSubmitRunnableSilentWithParent(final MockTracer tracer) throws InterruptedException {
    final CountDownLatch countDownLatch = new CountDownLatch(1);

    try (final Scope scope = tracer.buildSpan("parent").startActive(true)) {
      executorService.submit(new TestRunnable(tracer, countDownLatch));
    }

    countDownLatch.await();
    assertFalse(tracer.finishedSpans().isEmpty());
  }

  @Test
  @AgentRunner.TestConfig(verbose=true)
  public void testSubmitRunnableTypedVerbose(final MockTracer tracer) throws InterruptedException {
    final CountDownLatch countDownLatch = new CountDownLatch(1);

    executorService.submit(new TestRunnable(tracer, countDownLatch), new Object());

    countDownLatch.await();
    assertFalse(tracer.finishedSpans().isEmpty());
  }

  @Test
  @AgentRunner.TestConfig(verbose=false)
  public void testSubmitRunnableTypedSilent(final MockTracer tracer) throws InterruptedException {
    final CountDownLatch countDownLatch = new CountDownLatch(1);

    executorService.submit(new TestRunnable(tracer, countDownLatch), new Object());

    countDownLatch.await();
    assertFalse(tracer.finishedSpans().isEmpty());
  }

  @Test
  @AgentRunner.TestConfig(verbose=false)
  public void testSubmitRunnableTypedSilentWithParent(final MockTracer tracer) throws InterruptedException {
    final CountDownLatch countDownLatch = new CountDownLatch(1);

    try (final Scope scope = tracer.buildSpan("parent").startActive(true)) {
      executorService.submit(new TestRunnable(tracer, countDownLatch), new Object());
    }

    countDownLatch.await();
    assertFalse(tracer.finishedSpans().isEmpty());
  }

  @Test
  @AgentRunner.TestConfig(verbose=true)
  public void testSubmitCallableVerbose(final MockTracer tracer) throws InterruptedException {
    final CountDownLatch countDownLatch = new CountDownLatch(1);

    executorService.submit(new TestCallable(tracer, countDownLatch));

    countDownLatch.await();
    assertFalse(tracer.finishedSpans().isEmpty());
  }

  @Test
  @AgentRunner.TestConfig(verbose=false)
  public void testSubmitCallableSilent(final MockTracer tracer) throws InterruptedException {
    final CountDownLatch countDownLatch = new CountDownLatch(1);

    executorService.submit(new TestCallable(tracer, countDownLatch));

    countDownLatch.await();
    assertFalse(tracer.finishedSpans().isEmpty());
  }

  @Test
  @AgentRunner.TestConfig(verbose=false)
  public void testSubmitCallableSilentWithParent(final MockTracer tracer) throws InterruptedException {
    final CountDownLatch countDownLatch = new CountDownLatch(1);

    try (final Scope scope = tracer.buildSpan("parent").startActive(true)) {
      executorService.submit(new TestCallable(tracer, countDownLatch));
    }

    countDownLatch.await();
    assertFalse(tracer.finishedSpans().isEmpty());
  }

  @Test
  @AgentRunner.TestConfig(verbose=true)
  public void testInvokeAllVerbose(final MockTracer tracer) throws InterruptedException {
    final CountDownLatch countDownLatch = new CountDownLatch(2);

    executorService.invokeAll(Arrays.asList(new TestCallable(tracer, countDownLatch), new TestCallable(tracer, countDownLatch)));

    countDownLatch.await();
    assertFalse(tracer.finishedSpans().isEmpty());
  }

  @Test
  @AgentRunner.TestConfig(verbose=false)
  public void testInvokeAllSilent(final MockTracer tracer) throws InterruptedException {
    final CountDownLatch countDownLatch = new CountDownLatch(2);

    executorService.invokeAll(Arrays.asList(new TestCallable(tracer, countDownLatch), new TestCallable(tracer, countDownLatch)));

    countDownLatch.await();
    assertFalse(tracer.finishedSpans().isEmpty());
  }

  @Test
  @AgentRunner.TestConfig(verbose=false)
  public void testInvokeAllSilentWithParent(final MockTracer tracer) throws InterruptedException {
    final CountDownLatch countDownLatch = new CountDownLatch(2);

    try (final Scope scope = tracer.buildSpan("parent").startActive(true)) {
      executorService.invokeAll(Arrays.asList(new TestCallable(tracer, countDownLatch), new TestCallable(tracer, countDownLatch)));
    }

    countDownLatch.await();
    assertFalse(tracer.finishedSpans().isEmpty());
  }

  @Test
  @AgentRunner.TestConfig(verbose=true)
  public void testInvokeAllTimeUnitVerbose(final MockTracer tracer) throws InterruptedException {
    final CountDownLatch countDownLatch = new CountDownLatch(2);

    executorService.invokeAll(Arrays.asList(new TestCallable(tracer, countDownLatch), new TestCallable(tracer, countDownLatch)), 1, TimeUnit.SECONDS);

    countDownLatch.await();
    assertFalse(tracer.finishedSpans().isEmpty());
  }

  @Test
  @AgentRunner.TestConfig(verbose=false)
  public void testInvokeAllTimeUnitSilent(final MockTracer tracer) throws InterruptedException {
    final CountDownLatch countDownLatch = new CountDownLatch(2);

    executorService.invokeAll(Arrays.asList(new TestCallable(tracer, countDownLatch), new TestCallable(tracer, countDownLatch)), 1, TimeUnit.SECONDS);

    countDownLatch.await();
    assertFalse(tracer.finishedSpans().isEmpty());
  }

  @Test
  @AgentRunner.TestConfig(verbose=false)
  public void testInvokeAllTimeUnitSilentWithParent(final MockTracer tracer) throws InterruptedException {
    final CountDownLatch countDownLatch = new CountDownLatch(2);

    try (final Scope scope = tracer.buildSpan("parent").startActive(true)) {
      executorService.invokeAll(Arrays.asList(new TestCallable(tracer, countDownLatch), new TestCallable(tracer, countDownLatch)), 1, TimeUnit.SECONDS);
    }

    countDownLatch.await();
    assertFalse(tracer.finishedSpans().isEmpty());
  }

  @Test
  @AgentRunner.TestConfig(verbose=true)
  public void testInvokeAnyTimeUnitVerbose(final MockTracer tracer) throws InterruptedException, ExecutionException, TimeoutException {
    final CountDownLatch countDownLatch = new CountDownLatch(1);

    executorService.invokeAny(Arrays.asList(new TestCallable(tracer, countDownLatch)), 1, TimeUnit.SECONDS);

    countDownLatch.await();
    assertFalse(tracer.finishedSpans().isEmpty());
  }

  @Test
  @AgentRunner.TestConfig(verbose=false)
  public void testInvokeAnyTimeUnitSilent(final MockTracer tracer) throws InterruptedException, ExecutionException, TimeoutException {
    final CountDownLatch countDownLatch = new CountDownLatch(1);

    executorService.invokeAny(Arrays.asList(new TestCallable(tracer, countDownLatch)), 1, TimeUnit.SECONDS);

    countDownLatch.await();
    assertFalse(tracer.finishedSpans().isEmpty());
  }

  @Test
  @AgentRunner.TestConfig(verbose=false)
  public void testInvokeAnyTimeUnitSilentWithParent(final MockTracer tracer) throws InterruptedException, ExecutionException, TimeoutException {
    final CountDownLatch countDownLatch = new CountDownLatch(1);

    try (final Scope scope = tracer.buildSpan("parent").startActive(true)) {
      executorService.invokeAny(Arrays.asList(new TestCallable(tracer, countDownLatch)), 1, TimeUnit.SECONDS);
    }

    countDownLatch.await();
    assertFalse(tracer.finishedSpans().isEmpty());
  }

  @Test
  @AgentRunner.TestConfig(verbose=true)
  public void testInvokeAnyVerbose(final MockTracer tracer) throws InterruptedException, ExecutionException {
    final CountDownLatch countDownLatch = new CountDownLatch(1);

    executorService.invokeAny(Arrays.asList(new TestCallable(tracer, countDownLatch)));

    countDownLatch.await();
    assertFalse(tracer.finishedSpans().isEmpty());
  }

  @Test
  @AgentRunner.TestConfig(verbose=false)
  public void testInvokeAnySilent(final MockTracer tracer) throws InterruptedException, ExecutionException {
    final CountDownLatch countDownLatch = new CountDownLatch(1);

    executorService.invokeAny(Arrays.asList(new TestCallable(tracer, countDownLatch)));

    countDownLatch.await();
    assertFalse(tracer.finishedSpans().isEmpty());
  }

  @Test
  @AgentRunner.TestConfig(verbose=false)
  public void testInvokeAnySilentWithParent(final MockTracer tracer) throws InterruptedException, ExecutionException {
    final CountDownLatch countDownLatch = new CountDownLatch(1);

    try (final Scope scope = tracer.buildSpan("parent").startActive(true)) {
      executorService.invokeAny(Arrays.asList(new TestCallable(tracer, countDownLatch)));
    }

    countDownLatch.await();
    assertFalse(tracer.finishedSpans().isEmpty());
  }
}