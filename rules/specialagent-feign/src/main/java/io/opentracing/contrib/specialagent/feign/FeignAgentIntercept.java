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

package io.opentracing.contrib.specialagent.feign;

import feign.Request;
import feign.Request.Options;
import feign.Response;
import feign.opentracing.FeignSpanDecorator.StandardTags;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.propagation.Format;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class FeignAgentIntercept {
  private static class Context {
    private Scope scope;
    private Span span;
  }
  private static final StandardTags standardTags = new StandardTags();
  private static ThreadLocal<Context> contextHolder = new ThreadLocal<>();

  public static Object onRequest(Object arg1, Object arg2) {
    Request request = (Request) arg1;
    Options options = (Options) arg2;

    Span span = GlobalTracer.get().buildSpan(request.method())
        .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT)
        .start();

    standardTags.onRequest(request, options, span);

    request = inject(span.context(), request);

    final Scope scope = GlobalTracer.get().activateSpan(span);
    final Context context = new Context();
    contextHolder.set(context);
    context.scope = scope;
    context.span = span;

    return request;
  }

  private static Request inject(SpanContext spanContext, Request request) {
    Map<String, Collection<String>> headersWithTracingContext = new HashMap<>(request.headers());
    GlobalTracer.get().inject(spanContext, Format.Builtin.HTTP_HEADERS,
        new HttpHeadersInjectAdapter(headersWithTracingContext));
    return request
        .create(request.method(), request.url(), headersWithTracingContext, request.body(),
            request.charset());
  }

  public static void onResponse(Object arg1, Object arg2, Object arg3, Exception ex) {
    Response response = (Response) arg1;
    Request request = (Request) arg2;
    Options options = (Options) arg3;

    if (ex != null) {
      standardTags.onError(ex, request, GlobalTracer.get().activeSpan());
      finish();
      return;
    }

    standardTags.onResponse(response, options, GlobalTracer.get().activeSpan());
    finish();
  }

  private static void finish() {
    final Context context = contextHolder.get();
    if (context != null) {
      context.scope.close();
      context.span.finish();
      contextHolder.remove();
    }
  }
}