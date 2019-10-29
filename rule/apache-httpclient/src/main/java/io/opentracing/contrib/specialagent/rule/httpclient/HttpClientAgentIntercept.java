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

package io.opentracing.contrib.specialagent.rule.httpclient;

import java.net.URI;
import java.util.HashMap;

import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpUriRequest;

import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format.Builtin;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;

public class HttpClientAgentIntercept {
  private static class Context {
    private int counter = 1;
    private Span span;
  }

  static final String COMPONENT_NAME = "java-httpclient";
  private static final ThreadLocal<Context> contextHolder = new ThreadLocal<>();

  public static Object[] enter(final Object arg0, final Object arg1, final Object arg2) {
    final HttpRequest request = arg0 instanceof HttpRequest ? (HttpRequest)arg0 : arg1 instanceof HttpRequest ? (HttpRequest)arg1 : null;
    if (request == null)
      return null;

    if(request.getHeaders("amz-sdk-invocation-id").length > 0) {
      // skip embedded Apache HttpClient in AWS SDK Client, because it breaks
      // request signature and AWS SDK gets traced by the aws-sdk rule
      return null;
    }

    Context context = contextHolder.get();
    if (context != null) {
      ++context.counter;
      return null;
    }

    context = new Context();
    contextHolder.set(context);

    final Tracer tracer = GlobalTracer.get();
    final Span span = tracer
      .buildSpan(request.getRequestLine().getMethod())
      .withTag(Tags.COMPONENT, COMPONENT_NAME)
      .withTag(Tags.HTTP_METHOD, request.getRequestLine().getMethod())
      .withTag(Tags.HTTP_URL, request.getRequestLine().getUri()).start();

    if (request instanceof HttpUriRequest) {
      final URI uri = ((HttpUriRequest)request).getURI();
      setPeerHostPort(span, uri.getHost(), uri.getPort());
    }
    else if (arg0 instanceof HttpHost) {
      final HttpHost httpHost = (HttpHost)arg0;
      setPeerHostPort(span, httpHost.getHostName(), httpHost.getPort());
    }

    tracer.inject(span.context(), Builtin.HTTP_HEADERS, new HttpHeadersInjectAdapter(request));
    context.span = span;
    if (arg1 instanceof ResponseHandler)
      return new Object[] {new TracingResponseHandler<>((ResponseHandler<?>)arg1, span)};

    if (arg2 instanceof ResponseHandler)
      return new Object[] {null, new TracingResponseHandler<>((ResponseHandler<?>)arg2, span)};

    return null;
  }

  private static void setPeerHostPort(final Span span, final String host, final int port) {
    span.setTag(Tags.PEER_HOSTNAME, host);
    if (port != -1)
      span.setTag(Tags.PEER_PORT, port);
  }

  public static void exit(final Object returned) {
    final Context context = contextHolder.get();
    if (context == null)
      return;

    if (--context.counter != 0)
      return;

    if (returned instanceof HttpResponse) {
      final HttpResponse response = (HttpResponse)returned;
      Tags.HTTP_STATUS.set(context.span, response.getStatusLine().getStatusCode());
    }

    context.span.finish();
    contextHolder.remove();
  }

  public static void onError(final Throwable thrown) {
    final Context context = contextHolder.get();
    if (context == null)
      return;

    if (--context.counter != 0)
      return;

    final HashMap<String,Object> errorLogs = new HashMap<>(2);
    errorLogs.put("event", Tags.ERROR.getKey());
    errorLogs.put("error.object", thrown);
    context.span.setTag(Tags.ERROR, Boolean.TRUE);
    context.span.log(errorLogs);
    context.span.finish();
    contextHolder.remove();
  }
}