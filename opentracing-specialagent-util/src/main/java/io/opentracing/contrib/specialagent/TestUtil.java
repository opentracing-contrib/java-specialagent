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

package io.opentracing.contrib.specialagent;

import java.lang.reflect.Field;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockSpan.LogEntry;
import io.opentracing.mock.MockTracer;
import io.opentracing.noop.NoopTracer;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;

public final class TestUtil {
  public static CountDownLatch initExpectedSpanLatch(final int expectedSpans) {
    if (!(TestUtil.getGlobalTracer() instanceof MockTracer))
      return null;

    final CountDownLatch latch = new CountDownLatch(expectedSpans);
    TestUtil.setGlobalTracer(new MockTracer() {
      @Override
      protected void onSpanFinished(final MockSpan mockSpan) {
        super.onSpanFinished(mockSpan);
        latch.countDown();
      }
    });

    return latch;
  }

  public static void checkSpan(final String component, final int spanCount) {
    try {
      checkSpan(component, spanCount, null);
    }
    catch (final InterruptedException e) {
    }
  }

  public static void checkSpan(final String component, final int spanCount, final CountDownLatch latch) throws InterruptedException {
    final Tracer tracer = getGlobalTracer();
    if (tracer instanceof NoopTracer)
      throw new AssertionError("No tracer is registered");

    if (!(tracer instanceof MockTracer))
      return;

    final MockTracer mockTracer = (MockTracer)tracer;
    if (latch != null)
      latch.await(15, TimeUnit.SECONDS);

    boolean found = false;
    System.out.println("Spans: " + mockTracer.finishedSpans());
    for (final MockSpan span : mockTracer.finishedSpans()) {
      printSpan(span);
      if (component.equals(span.tags().get(Tags.COMPONENT.getKey()))) {
        found = true;
        System.out.println("Found " + component + " span");
      }
    }

    if (!found)
      throw new AssertionError("ERROR: " + component + " span not found");

    if (mockTracer.finishedSpans().size() != spanCount)
      throw new AssertionError("ERROR: " + mockTracer.finishedSpans().size() + " spans instead of " + spanCount);

    mockTracer.reset();
  }

  private static void printSpan(final MockSpan span) {
    System.out.println("Span: " + span);
    System.out.println("\tComponent: " + span.tags().get(Tags.COMPONENT.getKey()));
    System.out.println("\tTags: " + span.tags());
    System.out.println("\tLogs: ");
    for (final LogEntry logEntry : span.logEntries())
      System.out.println("\t" + logEntry.fields());
  }

  public static void checkActiveSpan() {
    final Span span = GlobalTracer.get().activeSpan();
    System.out.println("Active span: " + span);
    if (span == null)
      throw new AssertionError("Error: no active span");
  }

  public static Callable<Integer> reportedSpansSize(final MockTracer tracer) {
    return new Callable<Integer>() {
      @Override
      public Integer call() throws Exception {
        return tracer.finishedSpans().size();
      }
    };
  }

  public static void retry(final Runnable runnable, final int maxRetries) throws Exception {
    for (int i = 1; i <= maxRetries; ++i) {
      try {
        runnable.run();
        return;
      }
      catch (final Throwable t) {
        if (i == maxRetries)
          throw t;
      }
    }
  }

  public static <T>T retry(final Callable<T> callable, final int maxRetries) throws Exception {
    for (int i = 1; i <= maxRetries; ++i) {
      try {
        return callable.call();
      }
      catch (final Throwable t) {
        if (i == maxRetries)
          throw t;
      }
    }

    return null;
  }

  public static Tracer getGlobalTracer() {
    try {
      final Field field = GlobalTracer.class.getDeclaredField("tracer");
      field.setAccessible(true);
      final Tracer tracer = (Tracer)field.get(null);
      field.setAccessible(false);
      return tracer;
    }
    catch (final IllegalAccessException | NoSuchFieldException e) {
      throw new IllegalStateException(e);
    }
  }

  public static void setGlobalTracer(final Tracer tracer) {
    try {
      final Field tracerField = GlobalTracer.class.getDeclaredField("tracer");
      tracerField.setAccessible(true);
      tracerField.set(null, tracer);
      tracerField.setAccessible(false);

      final Field isRegisteredField = GlobalTracer.class.getDeclaredField("isRegistered");
      isRegisteredField.setAccessible(true);
      isRegisteredField.set(null, true);
      isRegisteredField.setAccessible(false);
    }
    catch (final IllegalAccessException | NoSuchFieldException e) {
      throw new IllegalStateException(e);
    }
  }

  public static void resetTracer() {
    final Tracer tracer = getGlobalTracer();

    if (!(tracer instanceof MockTracer))
      return;

    ((MockTracer) tracer).reset();
  }

  private TestUtil() {
  }
}