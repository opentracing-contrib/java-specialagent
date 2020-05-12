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

package io.opentracing.contrib.specialagent.rule.httpurlconnection;

import java.net.HttpURLConnection;

import javax.net.ssl.HttpsURLConnection;

import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.contrib.specialagent.LocalSpanContext;
import io.opentracing.contrib.specialagent.OpenTracingApiUtil;
import io.opentracing.propagation.Format.Builtin;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;

public class HttpURLConnectionAgentIntercept {
  static final String COMPONENT_NAME = "http-url-connection";

  public static void enter(final Object thiz, final boolean connected) {
    if (LocalSpanContext.get(COMPONENT_NAME) != null) {
      LocalSpanContext.get(COMPONENT_NAME).increment();
      return;
    }

    if (connected)
      return;

    final HttpURLConnection connection = (HttpURLConnection)thiz;
    final Tracer tracer = GlobalTracer.get();
    final SpanContext spanContext = tracer.extract(Builtin.HTTP_HEADERS, new HttpURLConnectionExtractAdapter(connection));

    if (spanContext != null)
      return;

    final Span span = tracer.buildSpan(connection.getRequestMethod())
      .withTag(Tags.COMPONENT, COMPONENT_NAME)
      .withTag(Tags.HTTP_METHOD, connection.getRequestMethod())
      .withTag(Tags.HTTP_URL, connection.getURL().toString())
      .withTag(Tags.PEER_PORT, getPort(connection))
      .withTag(Tags.PEER_HOSTNAME, connection.getURL().getHost()).start();

    final Scope scope = tracer.activateSpan(span);
    tracer.inject(span.context(), Builtin.HTTP_HEADERS, new HttpURLConnectionInjectAdapter(connection));

    LocalSpanContext.set(COMPONENT_NAME, span, scope);
  }

  public static void exit(final Throwable thrown, int responseCode) {
    final LocalSpanContext context = LocalSpanContext.get(COMPONENT_NAME);
    if (context == null)
      return;

    if (context.decrementAndGet() != 0)
      return;

    if (thrown != null)
      OpenTracingApiUtil.setErrorTag(context.getSpan(), thrown);
    else
      context.getSpan().setTag(Tags.HTTP_STATUS, responseCode);

    context.closeAndFinish();
  }

  private static Integer getPort(final HttpURLConnection connection) {
    final int port = connection.getURL().getPort();
    if (port > 0)
      return port;

    if (connection instanceof HttpsURLConnection)
      return 443;

    return 80;
  }
}