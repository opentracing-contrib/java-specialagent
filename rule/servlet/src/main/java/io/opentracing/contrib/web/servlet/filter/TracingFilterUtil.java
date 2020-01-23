/*
 * Copyright 2020 The OpenTracing Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package io.opentracing.contrib.web.servlet.filter;

import static io.opentracing.contrib.web.servlet.filter.TracingFilter.SERVER_SPAN_CONTEXT;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;
import io.opentracing.tag.Tags;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class TracingFilterUtil {
  public static Span buildSpan(HttpServletRequest httpRequest, Tracer tracer, List<ServletFilterSpanDecorator> spanDecorators) {
    SpanContext extractedContext = tracer.extract(Format.Builtin.HTTP_HEADERS, new HttpServletRequestExtractAdapter(httpRequest));

    final Span span = tracer.buildSpan(httpRequest.getMethod())
        .asChildOf(extractedContext)
        .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_SERVER)
        .start();

    httpRequest.setAttribute(SERVER_SPAN_CONTEXT, span.context());

    for (ServletFilterSpanDecorator spanDecorator: spanDecorators) {
      spanDecorator.onRequest(httpRequest, span);
    }

    return span;
  }

  public static void onResponse(HttpServletRequest httpRequest, HttpServletResponse httpResponse, Span span, List<ServletFilterSpanDecorator> spanDecorators) {
    for (ServletFilterSpanDecorator spanDecorator : spanDecorators) {
      spanDecorator.onResponse(httpRequest, httpResponse, span);
    }
  }

  public static void onError(HttpServletRequest httpRequest, HttpServletResponse httpResponse, Throwable ex, Span span, List<ServletFilterSpanDecorator> spanDecorators) {
    for (ServletFilterSpanDecorator spanDecorator : spanDecorators) {
      spanDecorator.onError(httpRequest, httpResponse, ex, span);
    }
  }
}
