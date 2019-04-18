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

import io.opentracing.Tracer;
import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.junit.After;
import org.junit.Before;

/**
 * @author Pavol Loffay
 * @author Jose Montoya
 * @author Seva Safris
 */
public abstract class AbstractConcurrentTest {

  protected void assertParentSpan(final MockTracer tracer) {
    final List<MockSpan> spans = tracer.finishedSpans();
    if (spans.size() <= 1) {
      return;
    }
    MockSpan parent = spans.get(0);
    for (int i = 1; i < spans.size(); i++) {
      assertEquals(parent.context().traceId(), spans.get(i).context().traceId());
    }
  }

  @Before
  public void reset(final MockTracer tracer) {
    tracer.reset();
  }

  @After
  public void after(final MockTracer tracer) {
    tracer.reset();
  }

  class TestRunnable implements Runnable {
    private final Tracer tracer;
    private final CountDownLatch countDownLatch;

    TestRunnable(final Tracer tracer, final CountDownLatch countDownLatch) {
      this.tracer = tracer;
      this.countDownLatch = countDownLatch;
    }

    @Override
    public void run() {
      try {
        tracer.buildSpan("childRunnable").start().finish();
      }
      finally {
        countDownLatch.countDown();
      }
    }
  }

  class TestCallable implements Callable<Void> {
    private final Tracer tracer;
    private final CountDownLatch countDownLatch;

    TestCallable(final Tracer tracer, final CountDownLatch countDownLatch) {
      this.tracer = tracer;
      this.countDownLatch = countDownLatch;
    }

    @Override
    public Void call() {
      try {
        tracer.buildSpan("childCallable").start().finish();
        return null;
      }
      finally {
        countDownLatch.countDown();
      }
    }
  }
}