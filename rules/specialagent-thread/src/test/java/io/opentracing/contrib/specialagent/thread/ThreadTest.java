/* Copyright 2019 The OpenTracing Authors
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

package io.opentracing.contrib.specialagent.thread;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import io.opentracing.Scope;
import io.opentracing.contrib.specialagent.AgentRunner;
import io.opentracing.mock.MockTracer;
import io.opentracing.util.GlobalTracer;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AgentRunner.class)
public class ThreadTest {

  @Before
  public void before(MockTracer tracer) {
    tracer.reset();
  }

  @Test
  public void test(final MockTracer tracer) throws InterruptedException {
    final AtomicBoolean foundSpan = new AtomicBoolean(false);
    Thread thread = new Thread(new Runnable() {
      @Override
      public void run() {
        foundSpan.set(true);
        throw new RuntimeException("error");
      }
    });
    try (Scope scope = tracer.buildSpan("parent").startActive(true)) {
      thread.start();
    }
    thread.join(10_000);

    assertTrue(foundSpan.get());

    assertEquals(1, tracer.finishedSpans().size());
    assertNull(GlobalTracer.get().activeSpan());
  }

  @Test
  public void testCustomThread(final MockTracer tracer) throws InterruptedException {
    final AtomicBoolean foundSpan = new AtomicBoolean(false);
    Thread thread = new CustomThread(new Runnable() {
      @Override
      public void run() {
        foundSpan.set(true);
      }
    });
    try (Scope scope = tracer.buildSpan("parent").startActive(true)) {
      thread.start();
    }
    thread.join(10_000);

    assertTrue(foundSpan.get());

    assertEquals(1, tracer.finishedSpans().size());
    assertNull(GlobalTracer.get().activeSpan());
  }

  private static class CustomThread extends Thread {
    CustomThread(Runnable runnable) {
      super(runnable);
    }
  }
}
