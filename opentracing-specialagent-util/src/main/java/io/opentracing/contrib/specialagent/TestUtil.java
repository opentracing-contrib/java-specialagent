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
import java.net.ServerSocket;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockSpan.LogEntry;
import io.opentracing.mock.MockTracer;
import io.opentracing.noop.NoopTracer;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;

public final class TestUtil {
  private static final int MIN_PORT = 15000;
  private static final int MAX_PORT = 32000;
  private static final AtomicInteger port = new AtomicInteger(MIN_PORT);

  public static int nextFreePort() {
    for (int p; (p = port.getAndIncrement()) <= MAX_PORT;) {
      try (final ServerSocket socket = new ServerSocket(p)) {
        return p;
      }
      catch (final Exception ignore) {
      }
    }

    throw new RuntimeException("Unable to find a free port");
  }

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

  public static void checkSpan(final ComponentSpanCount ... componentSpanCounts) {
    try {
      checkSpan(false, null, componentSpanCounts);
    }
    catch (final InterruptedException e) {
    }
  }

  public static void checkSpan(final boolean sameTrace, final ComponentSpanCount ... componentSpanCounts) {
    try {
      checkSpan(sameTrace, null, componentSpanCounts);
    }
    catch (final InterruptedException e) {
    }
  }

  public static void checkSpan(final CountDownLatch latch, final ComponentSpanCount ... componentSpanCounts) throws InterruptedException {
    checkSpan(false, latch, componentSpanCounts);
  }

  public static void checkSpan(final boolean sameTrace, final CountDownLatch latch, final ComponentSpanCount ... componentSpanCounts) throws InterruptedException {
    final MockTracer tracer = getTracer();
    if (tracer == null)
      return;

    if (latch != null)
      latch.await(1, TimeUnit.MINUTES);

    printSpans(tracer);

    final List<MockSpan> spans = tracer.finishedSpans();
    final Map<String,Integer> spanCountMap = new HashMap<>();
    final Map<String,Long> traceIdMap = new HashMap<>();
    final Set<Long> traceIds = new HashSet<>();
    for (final MockSpan span : spans) {
      printSpan(span);
      for (final ComponentSpanCount componentSpanCount : componentSpanCounts) {
        final String spanComponent = (String)span.tags().get(Tags.COMPONENT.getKey());
        if (spanComponent == null || !spanComponent.matches(componentSpanCount.componentName))
          continue;

        if (!spanCountMap.containsKey(componentSpanCount.componentName))
          spanCountMap.put(componentSpanCount.componentName, 1);
        else
          spanCountMap.put(componentSpanCount.componentName, spanCountMap.get(componentSpanCount.componentName) + 1);

        if (componentSpanCount.sameTrace && traceIdMap.containsKey(componentSpanCount.componentName) && !traceIdMap.get(componentSpanCount.componentName).equals(span.context().traceId()))
          throw new AssertionError("Not the same trace of " + componentSpanCount.componentName);

        traceIds.add(span.context().traceId());
        traceIdMap.put(componentSpanCount.componentName, span.context().traceId());
      }
    }

    if (sameTrace && traceIds.size() > 1) {
      final Iterator<Long> iterator = traceIds.iterator();
      final long traceId = iterator.next();
      while (iterator.hasNext())
        if (iterator.next() != traceId)
          throw new AssertionError("Not the same trace");
    }

    for (final ComponentSpanCount componentSpanCount : componentSpanCounts) {
      if (!spanCountMap.containsKey(componentSpanCount.componentName))
        throw new AssertionError("\"" + componentSpanCount.componentName + "\" span not found");

      if (spanCountMap.get(componentSpanCount.componentName) != componentSpanCount.count)
        throw new AssertionError(spanCountMap.get(componentSpanCount.componentName) + " spans instead of " + componentSpanCount.count + " of " + componentSpanCount.componentName);
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

  public static class ComponentSpanCount {
    private final String componentName;
    private final int count;
    private final boolean sameTrace;

    public ComponentSpanCount(final String componentName, final int count) {
      this(componentName, count, false);
    }

    public ComponentSpanCount(final String componentName, final int count, final boolean sameTrace) {
      this.componentName = componentName;
      this.count = count;
      this.sameTrace = sameTrace;
    }
  }

  private TestUtil() {
  }
}