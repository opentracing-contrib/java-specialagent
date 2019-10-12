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
import java.util.concurrent.TimeUnit;

import io.opentracing.Span;
import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockSpan.LogEntry;
import io.opentracing.mock.MockTracer;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;

public final class TestUtil {
  public static void checkSpan(final String component, final int spanCount) throws Exception {
    final Field field = GlobalTracer.get().getClass().getDeclaredField("tracer");
    field.setAccessible(true);
    final Object obj = field.get(GlobalTracer.get());
    MockTracer tracer;
    if (obj instanceof MockTracer) {
      tracer = (MockTracer)obj;
    }
    else {
      TimeUnit.SECONDS.sleep(10);
      return;
    }

    for (int i = 0; tracer.finishedSpans().size() < spanCount && i < 10; ++i)
      TimeUnit.SECONDS.sleep(1L);

    boolean found = false;
    System.out.println("Spans: " + tracer.finishedSpans());
    for (final MockSpan span : tracer.finishedSpans()) {
      printSpan(span);
      if (component.equals(span.tags().get(Tags.COMPONENT.getKey()))) {
        found = true;
        System.out.println("Found " + component + " span");
      }
    }

    if (!found)
      throw new AssertionError("ERROR: " + component + " span not found");

    if (tracer.finishedSpans().size() != spanCount)
      throw new AssertionError("ERROR: " + tracer.finishedSpans().size() + " spans instead of " + spanCount);
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

  private TestUtil() {
  }
}