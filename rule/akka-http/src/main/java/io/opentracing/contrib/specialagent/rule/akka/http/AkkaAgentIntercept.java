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

import akka.japi.Function;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format.Builtin;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;

public class AkkaAgentIntercept {
  private static final ThreadLocal<Context> contextHolder = new ThreadLocal<>();
  static final String COMPONENT_NAME_CLIENT = "akka-http-client";
  static final String COMPONENT_NAME_SERVER = "akka-http-server";

  private static class Context {
    private Span span;
    private Scope scope;
    private int counter = 1;
  }

  public static Object requestStart(final Object arg0) {
    if (contextHolder.get() != null) {
      ++contextHolder.get().counter;
      return arg0;
    }

    final HttpRequest request = (HttpRequest)arg0;
    final Tracer tracer = GlobalTracer.get();
    final Span span = tracer.buildSpan(request.method().value())
      .withTag(Tags.COMPONENT, COMPONENT_NAME_CLIENT)
      .withTag(Tags.SPAN_KIND, Tags.SPAN_KIND_CLIENT)
      .withTag(Tags.HTTP_METHOD, request.method().value())
      .withTag(Tags.HTTP_URL, request.getUri().toString())
      .withTag(Tags.PEER_HOSTNAME, request.getUri().host().address())
      .withTag(Tags.PEER_PORT, request.getUri().port())
      .start();

    final HttpHeadersInjectAdapter injectAdapter = new HttpHeadersInjectAdapter(request);
    tracer.inject(span.context(), Builtin.HTTP_HEADERS, injectAdapter);

    final Context context = new Context();
    contextHolder.set(context);
    context.span = span;
    context.scope = tracer.activateSpan(span);

    return injectAdapter.getHttpRequest();
  }

  @SuppressWarnings("unchecked")
  public static Object requestEnd(final Object returned, final Throwable thrown) {
    final Context context = contextHolder.get();
    if (context == null)
      return returned;

    if (--context.counter != 0)
      return returned;

    final Span span = context.span;
    context.scope.close();
    contextHolder.remove();

    if (thrown != null) {
      onError(thrown, span);
      span.finish();
      return returned;
    }

    final CompletionStage<HttpResponse> stage = (CompletionStage<HttpResponse>)returned;
    return stage.thenApply(httpResponse -> {
      span.setTag(Tags.HTTP_STATUS, httpResponse.status().intValue());
      span.finish();
      return httpResponse;
    }).exceptionally(throwable -> {
      onError(throwable, span);
      span.finish();
      return null;
    });
  }

  @SuppressWarnings("unchecked")
  public static Object bindAndHandleSync(Object arg0) {
    Function<HttpRequest, HttpResponse> handler = (Function<HttpRequest, HttpResponse>) arg0;
    return new AkkaHttpSyncHandler(handler);
  }

  @SuppressWarnings("unchecked")
  public static Object bindAndHandleAsync(Object arg0) {
    Function<HttpRequest, CompletableFuture<HttpResponse>> handler = (Function<HttpRequest, CompletableFuture<HttpResponse>>) arg0;
    return new AkkaHttpAsyncHandler(handler);
  }

  static void onError(final Throwable t, final Span span) {
    Tags.ERROR.set(span, Boolean.TRUE);
    if (t != null)
      span.log(errorLogs(t));
  }

  private static Map<String,Object> errorLogs(final Throwable t) {
    final Map<String,Object> errorLogs = new HashMap<>(2);
    errorLogs.put("event", Tags.ERROR.getKey());
    errorLogs.put("error.object", t);
    return errorLogs;
  }
}