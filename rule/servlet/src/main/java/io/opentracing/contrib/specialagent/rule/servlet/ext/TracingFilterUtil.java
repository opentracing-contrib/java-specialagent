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

package io.opentracing.contrib.specialagent.rule.servlet.ext;

import static io.opentracing.contrib.web.servlet.filter.TracingFilter.*;

import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.contrib.web.servlet.filter.HttpServletRequestExtractAdapter;
import io.opentracing.contrib.web.servlet.filter.ServletFilterSpanDecorator;
import io.opentracing.propagation.Format;
import io.opentracing.tag.Tags;

public class TracingFilterUtil {
  public static Span buildSpan(final HttpServletRequest httpRequest, final Tracer tracer, final List<ServletFilterSpanDecorator> spanDecorators) {
    final SpanContext extractedContext = tracer.extract(Format.Builtin.HTTP_HEADERS, new HttpServletRequestExtractAdapter(httpRequest));
    final Span span = tracer.buildSpan(httpRequest.getMethod())
      .asChildOf(extractedContext)
      .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_SERVER)
      .start();

    httpRequest.setAttribute(SERVER_SPAN_CONTEXT, span.context());
    for (final ServletFilterSpanDecorator spanDecorator: spanDecorators)
      spanDecorator.onRequest(httpRequest, span);

    return span;
  }

  public static void onResponse(final HttpServletRequest httpRequest, final HttpServletResponse httpResponse, final Span span, final List<ServletFilterSpanDecorator> spanDecorators) {
    for (final ServletFilterSpanDecorator spanDecorator : spanDecorators)
      spanDecorator.onResponse(httpRequest, httpResponse, span);
  }

  public static void onError(final HttpServletRequest httpRequest, final HttpServletResponse httpResponse, final Throwable t, final Span span, final List<ServletFilterSpanDecorator> spanDecorators) {
    for (final ServletFilterSpanDecorator spanDecorator : spanDecorators)
      spanDecorator.onError(httpRequest, httpResponse, t, span);
  }
}