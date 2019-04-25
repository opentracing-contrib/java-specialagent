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

package io.opentracing.contrib.specialagent.httpclient;

import io.opentracing.Span;
import io.opentracing.propagation.Format.Builtin;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import java.util.HashMap;
import java.util.Map;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpUriRequest;

public class HttpClientAgentIntercept {
  private static class Context {
    private int counter = 1;
    private Span span;
  }

  static final String COMPONENT_NAME = "java-httpclient";
  private static final ThreadLocal<Context> contextHolder = new ThreadLocal<>();

  public static Object[] enter(Object arg, Object arg2, Object arg3) {
    HttpRequest request = null;
    if (arg instanceof HttpRequest) {
      request = (HttpRequest) arg;
    } else if (arg2 instanceof HttpRequest) {
      request = (HttpRequest) arg2;
    }
    if (request == null) {
      return null;
    }
    if (contextHolder.get() != null) {
      ++contextHolder.get().counter;
      return null;
    }

    final Context context = new Context();
    contextHolder.set(context);

    Span span;
    span = createSpan(request);
    if (request instanceof HttpUriRequest) {
      HttpUriRequest uriRequest = (HttpUriRequest) request;
      Tags.PEER_HOSTNAME.set(span, uriRequest.getURI().getHost());
      Tags.PEER_PORT
          .set(span, uriRequest.getURI().getPort() == -1 ? 80 : uriRequest.getURI().getPort());
    }

    GlobalTracer.get()
        .inject(span.context(), Builtin.HTTP_HEADERS, new HttpHeadersInjectAdapter(request));

    contextHolder.get().span = span;

    if (arg2 instanceof ResponseHandler) {
      return new Object[]{new TracingResponseHandler<>((ResponseHandler<?>) arg2, span)};
    } else if (arg3 instanceof ResponseHandler) {
      return new Object[]{null, new TracingResponseHandler<>((ResponseHandler<?>) arg3, span)};
    }
    return null;
  }

  private static Span createSpan(HttpRequest request) {
    return GlobalTracer.get().buildSpan(request.getRequestLine().getMethod())
        .withTag(Tags.COMPONENT, COMPONENT_NAME)
        .withTag(Tags.HTTP_METHOD, request.getRequestLine().getMethod())
        .withTag(Tags.HTTP_URL, request.getRequestLine().getUri())
        .start();
  }

  public static void exit(Object returned) {
    final Context context = contextHolder.get();
    if (context == null) {
      return;
    }
    --context.counter;
    if (context.counter == 0) {
      if (returned instanceof HttpResponse) {
        HttpResponse response = (HttpResponse) returned;
        Tags.HTTP_STATUS.set(context.span, response.getStatusLine().getStatusCode());
      }
      context.span.finish();
      contextHolder.remove();
    }
  }

  public static void onError(Throwable thrown) {
    final Context context = contextHolder.get();
    if (context == null) {
      return;
    }
    --context.counter;
    if (context.counter == 0) {
      Tags.ERROR.set(context.span, Boolean.TRUE);

      Map<String, Object> errorLogs = new HashMap<>(2);
      errorLogs.put("event", Tags.ERROR.getKey());
      errorLogs.put("error.object", thrown);
      context.span.log(errorLogs);
      context.span.finish();
      contextHolder.remove();
    }
  }
}