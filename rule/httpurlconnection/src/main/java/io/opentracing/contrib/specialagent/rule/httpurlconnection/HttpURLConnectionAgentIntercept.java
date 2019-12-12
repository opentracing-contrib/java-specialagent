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
import java.util.HashMap;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;

import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format.Builtin;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;

public class HttpURLConnectionAgentIntercept {
  private static final ThreadLocal<Context> contextHolder = new ThreadLocal<>();
  static final String COMPONENT_NAME = "http-url-connection";

  private static class Context {
    private Span span;
    private Scope scope;
    private int counter = 1;
  }

  public static void enter(final Object thiz, final boolean connected) {
    if (contextHolder.get() != null) {
      ++contextHolder.get().counter;
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

    final Context context = new Context();
    contextHolder.set(context);
    context.span = span;
    context.scope = scope;
  }

  public static void exit(final Throwable thrown, int responseCode) {
    final Context context = contextHolder.get();
    if (context == null)
      return;

    if (--context.counter != 0)
      return;

    if (thrown != null)
      onError(thrown, context.span);
    else
      context.span.setTag(Tags.HTTP_STATUS, responseCode);

    context.scope.close();
    context.span.finish();
    contextHolder.remove();
  }

  private static Integer getPort(final HttpURLConnection connection) {
    final int port = connection.getURL().getPort();
    if (port > 0)
      return port;

    if (connection instanceof HttpsURLConnection)
      return 443;

    return 80;
  }

  private static void onError(final Throwable t, final Span span) {
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