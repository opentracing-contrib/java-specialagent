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

package io.opentracing.contrib.specialagent.rule.feign;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import feign.Request;
import feign.Request.Options;
import feign.Response;
import feign.opentracing.FeignSpanDecorator.StandardTags;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.contrib.specialagent.LocalSpanContext;
import io.opentracing.propagation.Format;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;

public class FeignAgentIntercept {
  private static final StandardTags standardTags = new StandardTags();

  public static Object onRequest(final Object arg1, final Object arg2) {
    Request request = (Request)arg1;
    final Tracer tracer = GlobalTracer.get();
    final Span span = tracer
      .buildSpan(request.method())
      .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT)
      .start();

    standardTags.onRequest(request, (Options)arg2, span);

    request = inject(span.context(), request);

    final Scope scope = tracer.activateSpan(span);
    LocalSpanContext.set(span, scope);

    return request;
  }

  private static Request inject(final SpanContext spanContext, final Request request) {
    final Map<String,Collection<String>> headersWithTracingContext = new HashMap<>(request.headers());
    GlobalTracer.get().inject(spanContext, Format.Builtin.HTTP_HEADERS, new HttpHeadersInjectAdapter(headersWithTracingContext));
    return Request.create(request.method(), request.url(), headersWithTracingContext, request.body(), request.charset());
  }

  public static void onResponse(final Object arg1, final Object arg2, final Object arg3, final Exception e) {
    final Response response = (Response)arg1;
    final Request request = (Request)arg2;
    final Options options = (Options)arg3;
    if (e == null)
      standardTags.onResponse(response, options, GlobalTracer.get().activeSpan());
    else
      standardTags.onError(e, request, GlobalTracer.get().activeSpan());

    finish();
  }

  private static void finish() {
    final LocalSpanContext context = LocalSpanContext.get();
    if (context != null) {
      context.closeAndFinish();
    }
  }
}