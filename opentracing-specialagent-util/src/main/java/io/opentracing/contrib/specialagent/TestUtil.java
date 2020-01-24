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
import java.util.List;
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

  private static MockTracer getTracer() {
    final Tracer tracer = getGlobalTracer();
    if (tracer instanceof NoopTracer)
      throw new AssertionError("No tracer is registered");

    return tracer instanceof MockTracer ? (MockTracer)tracer : null;
  }

  public static void printSpans() {
    final MockTracer tracer = getTracer();
    if (tracer != null)
      printSpans(tracer);
  }

  private static void printSpans(final MockTracer tracer) {
    final List<MockSpan> spans = tracer.finishedSpans();
    System.out.println("Spans: " + spans);
  }

  public static void checkSpan(final String component, final int spanCount) {
    try {
      checkSpan(component, spanCount, null, false);
    }
    catch (final InterruptedException e) {
    }
  }

  public static void checkSpan(final String component, final int spanCount, final boolean sameTrace) {
    try {
      checkSpan(component, spanCount, null, sameTrace);
    }
    catch (final InterruptedException e) {
    }
  }

  public static void checkSpan(final String component, final int spanCount, final CountDownLatch latch) throws InterruptedException {
    checkSpan(component, spanCount, latch, false);
  }

  public static void checkSpan(final String component, final int spanCount, final CountDownLatch latch, final boolean sameTrace) throws InterruptedException {
    final MockTracer tracer = getTracer();
    if (tracer == null)
      return;

    if (latch != null)
      latch.await(15, TimeUnit.SECONDS);

    printSpans(tracer);

    boolean found = false;
    final List<MockSpan> spans = tracer.finishedSpans();
    for (final MockSpan span : spans) {
      printSpan(span);
      final String spanComponent = (String)span.tags().get(Tags.COMPONENT.getKey());
      if (spanComponent != null && spanComponent.matches(component)) {
        found = true;
        System.out.println("Found \"" + component + "\" span");
      }
    }

    if (!found)
      throw new AssertionError("\"" + component + "\" span not found");

    if (spans.size() != spanCount)
      throw new AssertionError(spans.size() + " spans instead of " + spanCount);

    if (sameTrace && spans.size() > 1) {
      final long traceId = spans.get(0).context().traceId();
      for (int i = 1; i < spans.size(); ++i) {
        if (spans.get(i).context().traceId() != traceId) {
          throw new AssertionError("Not the same trace");
        }
      }
    }

    tracer.reset();
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
      throw new AssertionError("No active span");
  }

  public static Callable<Integer> reportedSpansSize(final MockTracer tracer) {
    return new Callable<Integer>() {
      @Override
      public Integer call() throws Exception {
        return tracer.finishedSpans().size();
      }
    };
  }

  public static void resetTracer() {
    final MockTracer tracer = getTracer();
    if (tracer != null)
      tracer.reset();
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

  public static <T> T retry(final Callable<T> callable, final int maxRetries) throws Exception {
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

  private TestUtil() {
  }
}