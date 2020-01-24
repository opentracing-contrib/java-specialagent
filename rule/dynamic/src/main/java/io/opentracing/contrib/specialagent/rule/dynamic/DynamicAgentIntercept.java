/* Copyright 2020 The OpenTracing Authors
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

package io.opentracing.contrib.specialagent.rule.dynamic;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Queue;

import io.opentracing.Span;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;

public class DynamicAgentIntercept {
  private static final ThreadLocal<Queue<Span>> spanHolder = new ThreadLocal<Queue<Span>>() {
    @Override
    protected Queue<Span> initialValue() {
      return new ArrayDeque<>();
    }
  };

  public static final String TAGS_KEY_SPAN_TYPE = "span.type";
  public static final String TAGS_KEY_ORIGIN = "origin";
  public static final String TAGS_KEY_ERROR_MESSAGE = "error.message";
  public static final String TAGS_KEY_ERROR = "error";
  public static final String TAGS_KEY_HTTP_STATUS_CODE = "http.status_code";

  public static final String TAGS_VALUE_INTERNAL = "internal";

  private static final String[] tokens = {"abstract", "final", "private", "protected", "public", "static", "throws", "synchronized", "void"};

  public static void enter(final String origin) {
    // parse public java.lang.String
    // com.test.HomeController.homePage(java.lang.String)

    String methodName = null;
    final String[] parts = origin.split(" ");
    for (final String part : parts) {
      if (Arrays.binarySearch(tokens, part) < 0) {
        final int par = part.lastIndexOf('(');
        if (par == -1)
          continue;

        final int dot = part.lastIndexOf('.', par - 1);
        methodName = part.substring(dot + 1, par);
        break;
      }
    }

    if (methodName == null)
      throw new IllegalStateException();

    final Span span = GlobalTracer.get()
      .buildSpan(methodName)
      .withTag(TAGS_KEY_SPAN_TYPE, TAGS_VALUE_INTERNAL)
      .withTag(TAGS_KEY_ORIGIN, origin)
      .withTag(Tags.COMPONENT.getKey(), "dynamic")
      .start();

    spanHolder.get().add(span);
  }

  public static void exit(final Throwable thrown) {
    final Queue<Span> spans = spanHolder.get();
    if (spans.isEmpty())
      return;

    final Span span = spans.poll();
    if (thrown != null) {
      span.log(errorLogs(thrown));
      span.setTag(TAGS_KEY_ERROR, true);
      span.setTag(TAGS_KEY_ERROR_MESSAGE, thrown.getMessage());
      span.setTag(TAGS_KEY_HTTP_STATUS_CODE, 500);
    }
    else {
      span.setTag(TAGS_KEY_HTTP_STATUS_CODE, 200);
    }

    span.finish();
  }

  private static HashMap<String,Object> errorLogs(final Throwable t) {
    final HashMap<String,Object> errorLogs = new HashMap<>(2);
    errorLogs.put("event", Tags.ERROR.getKey());
    errorLogs.put("error.object", toString(t));
    return errorLogs;
  }

  private static String toString(final Throwable t) {
    final StringWriter out = new StringWriter();
    t.printStackTrace(new PrintWriter(out));
    return out.toString();
  }
}