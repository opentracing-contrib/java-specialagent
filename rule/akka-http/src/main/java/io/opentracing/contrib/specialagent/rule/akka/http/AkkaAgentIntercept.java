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

package io.opentracing.contrib.specialagent.rule.akka.http;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.japi.Function;
import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.contrib.specialagent.LocalSpanContext;
import io.opentracing.contrib.specialagent.OpenTracingApiUtil;
import io.opentracing.propagation.Format.Builtin;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;

public class AkkaAgentIntercept {
  static final String COMPONENT_NAME_CLIENT = "akka-http-client";
  static final String COMPONENT_NAME_SERVER = "akka-http-server";

  public static Object requestStart(final Object arg0) {
    if (LocalSpanContext.get() != null) {
      LocalSpanContext.get().increment();
      return arg0;
    }

    final HttpRequest request = (HttpRequest)arg0;
    final Tracer tracer = GlobalTracer.get();
    final Span span = tracer
      .buildSpan(request.method().value())
      .withTag(Tags.COMPONENT, COMPONENT_NAME_CLIENT)
      .withTag(Tags.SPAN_KIND, Tags.SPAN_KIND_CLIENT)
      .withTag(Tags.HTTP_METHOD, request.method().value())
      .withTag(Tags.HTTP_URL, request.getUri().toString())
      .withTag(Tags.PEER_HOSTNAME, request.getUri().host().address())
      .withTag(Tags.PEER_PORT, request.getUri().port())
      .start();

    final HttpHeadersInjectAdapter injectAdapter = new HttpHeadersInjectAdapter(request);
    tracer.inject(span.context(), Builtin.HTTP_HEADERS, injectAdapter);

    LocalSpanContext.set(span, tracer.activateSpan(span));

    return injectAdapter.getHttpRequest();
  }

  @SuppressWarnings("unchecked")
  public static Object requestEnd(final Object returned, final Throwable thrown) {
    final LocalSpanContext context = LocalSpanContext.get();
    if (context == null || context.decrementAndGet() != 0)
      return returned;

    final Span span = context.getSpan();
    context.closeScope();

    if (thrown != null) {
      OpenTracingApiUtil.setErrorTag(span, thrown);
      span.finish();
      return returned;
    }

    return ((CompletionStage<HttpResponse>)returned).thenApply(httpResponse -> {
      span.setTag(Tags.HTTP_STATUS, httpResponse.status().intValue());
      span.finish();
      return httpResponse;
    }).exceptionally(throwable -> {
      OpenTracingApiUtil.setErrorTag(span, throwable);
      span.finish();
      return null;
    });
  }

  @SuppressWarnings("unchecked")
  public static Object bindAndHandleSync(final Object handler) {
    return new AkkaHttpSyncHandler((Function<HttpRequest,HttpResponse>)handler);
  }

  @SuppressWarnings("unchecked")
  public static Object bindAndHandleAsync(final Object handler) {
    return new AkkaHttpAsyncHandler((Function<HttpRequest,CompletableFuture<HttpResponse>>)handler);
  }
}