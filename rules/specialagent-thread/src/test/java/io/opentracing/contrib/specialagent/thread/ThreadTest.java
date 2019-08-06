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

import static org.junit.Assert.*;

import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.opentracing.Scope;
import io.opentracing.contrib.specialagent.AgentRunner;
import io.opentracing.mock.MockTracer;
import io.opentracing.util.GlobalTracer;

@RunWith(AgentRunner.class)
@AgentRunner.Config(isolateClassLoader = false)
public class ThreadTest {
  static {
    System.out.println(System.getProperty("sa.log.level"));
  }

  @Before
  public void before(final MockTracer tracer) {
    tracer.reset();
  }

  @Test
  public void test(final MockTracer tracer) throws InterruptedException {
    final AtomicBoolean foundSpan = new AtomicBoolean(false);
    final Thread thread = new Thread(new Runnable() {
      @Override
      public void run() {
        foundSpan.set(tracer.activeSpan() != null);
        throw new RuntimeException("error");
      }
    });

    try (final Scope scope = tracer.buildSpan("parent").startActive(true)) {
      thread.start();
    }

    thread.join(10_000);
    assertTrue(foundSpan.get());
    assertEquals(1, tracer.finishedSpans().size());
    assertNull(GlobalTracer.get().activeSpan());
  }

  @Test
  public void testNoRunnable(final MockTracer tracer) throws InterruptedException {
    final AtomicBoolean foundSpan = new AtomicBoolean(false);
    final Thread thread = new Thread() {
      @Override
      public void run() {
        foundSpan.set(tracer.activeSpan() != null);
      }
    };

    try (final Scope scope = tracer.buildSpan("parent").startActive(true)) {
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
    final Thread thread = new CustomThread(new Runnable() {
      @Override
      public void run() {
        foundSpan.set(tracer.activeSpan() != null);
      }
    });

    try (final Scope scope = tracer.buildSpan("parent").startActive(true)) {
      thread.start();
    }

    thread.join(10_000);
    assertTrue(foundSpan.get());
    assertEquals(1, tracer.finishedSpans().size());
    assertNull(GlobalTracer.get().activeSpan());
  }

  private static class CustomThread extends Thread {
    CustomThread(final Runnable runnable) {
      super(runnable);
    }
  }
}