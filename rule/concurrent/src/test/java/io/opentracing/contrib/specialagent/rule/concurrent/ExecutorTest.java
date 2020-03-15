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

import static org.junit.Assert.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.opentracing.Scope;
import io.opentracing.contrib.specialagent.AgentRunner;
import io.opentracing.mock.MockTracer;

/**
 * @author Pavol Loffay
 * @author Jose Montoya
 * @author Seva Safris
 */
@Ignore
@RunWith(AgentRunner.class)
public class ExecutorTest extends AbstractConcurrentTest {
  private ExecutorService executorService;

  @Before
  public void before() {
    executorService = Executors.newFixedThreadPool(10);
  }

  @After
  public void after() {
    if (executorService != null)
      executorService.shutdownNow();
  }

  @Test
  @AgentRunner.TestConfig(verbose=true)
  public void testExecuteVerbose(final MockTracer tracer) throws InterruptedException {
    final CountDownLatch countDownLatch = new CountDownLatch(1);
    executorService.execute(new TestRunnable(tracer, countDownLatch));

    countDownLatch.await();
    assertFalse(tracer.finishedSpans().isEmpty());
  }

  @Test
  @AgentRunner.TestConfig(verbose=false)
  public void testExecuteSilent(final MockTracer tracer) throws InterruptedException {
    final CountDownLatch countDownLatch = new CountDownLatch(1);
    executorService.execute(new TestRunnable(tracer, countDownLatch));

    countDownLatch.await();
    assertFalse(tracer.finishedSpans().isEmpty());
  }

  @Test
  @AgentRunner.TestConfig(verbose=false)
  public void testExecuteSilentWithParent(final MockTracer tracer) throws InterruptedException {
    final CountDownLatch countDownLatch = new CountDownLatch(1);
    try (final Scope scope = tracer.buildSpan("parent").startActive(true)) {
      executorService.execute(new TestRunnable(tracer, countDownLatch));
    }

    countDownLatch.await();
    assertFalse(tracer.finishedSpans().isEmpty());
  }
}