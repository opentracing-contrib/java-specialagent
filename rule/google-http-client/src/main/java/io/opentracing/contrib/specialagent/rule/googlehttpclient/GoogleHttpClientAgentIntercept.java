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

package io.opentracing.contrib.specialagent.rule.googlehttpclient;

import io.opentracing.contrib.specialagent.LocalSpanContext;
import io.opentracing.contrib.specialagent.SpanUtil;

import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpResponse;

import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format.Builtin;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;

public class GoogleHttpClientAgentIntercept {
  private static final ThreadLocal<LocalSpanContext> contextHolder = new ThreadLocal<>();
  static final String COMPONENT_NAME = "google-http-client";

  public static void enter(final Object thiz) {
    if (contextHolder.get() != null) {
      contextHolder.get().increment();
      return;
    }

    final Tracer tracer = GlobalTracer.get();
    HttpRequest request = (HttpRequest)thiz;

    final Span span = tracer
      .buildSpan(request.getRequestMethod())
      .withTag(Tags.COMPONENT, COMPONENT_NAME)
      .withTag(Tags.HTTP_METHOD, request.getRequestMethod())
      .withTag(Tags.HTTP_URL, request.getUrl().toString())
      .withTag(Tags.PEER_PORT, getPort(request))
      .withTag(Tags.PEER_HOSTNAME, request.getUrl().getHost()).start();

    final Scope scope = tracer.activateSpan(span);
    tracer.inject(span.context(), Builtin.HTTP_HEADERS, new HttpHeadersInjectAdapter(request.getHeaders()));

    final LocalSpanContext context = new LocalSpanContext(span, scope);
    contextHolder.set(context);
  }

  public static void exit(Throwable thrown, Object returned) {
    final LocalSpanContext context = contextHolder.get();
    if (context == null)
      return;

    if (context.decrementAndGet() != 0)
      return;

    if (thrown != null)
      SpanUtil.onError(thrown, context.getSpan());
    else
      context.getSpan().setTag(Tags.HTTP_STATUS, ((HttpResponse)returned).getStatusCode());

    context.closeAndFinish();
    contextHolder.remove();
  }

  private static Integer getPort(final HttpRequest httpRequest) {
    final int port = httpRequest.getUrl().getPort();
    if (port > 0)
      return port;

    if ("https".equals(httpRequest.getUrl().getScheme()))
      return 443;

    return 80;
  }

}