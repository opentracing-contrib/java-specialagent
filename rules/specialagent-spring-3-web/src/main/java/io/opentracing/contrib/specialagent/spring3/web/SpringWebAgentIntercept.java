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

package io.opentracing.contrib.specialagent.spring3.web;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpResponse;

import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.contrib.specialagent.spring3.web.copied.HttpHeadersCarrier;
import io.opentracing.propagation.Format.Builtin;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;

public class SpringWebAgentIntercept {
  private static final ThreadLocal<Context> contextHolder = new ThreadLocal<>();

  private static class Context {
    private Scope scope;
    private Span span;
  }

  public static void enter(final Object thiz) {
    try {
      Class.forName("org.springframework.beans.factory.access.BeanFactoryLocator");
    }
    catch (final ClassNotFoundException ignore) {
      // Spring 5.x doesn't have it
      return;
    }

    final ClientHttpRequest request = (ClientHttpRequest)thiz;
    final Tracer tracer = GlobalTracer.get();
    final Span span = tracer
      .buildSpan(request.getMethod().name())
      .withTag(Tags.COMPONENT.getKey(), "java-spring-rest-template")
      .withTag(Tags.SPAN_KIND, Tags.SPAN_KIND_CLIENT)
      .withTag(Tags.HTTP_URL, request.getURI().toString())
      .withTag(Tags.HTTP_METHOD, request.getMethod().name())
      .start();

    tracer.inject(span.context(), Builtin.HTTP_HEADERS, new HttpHeadersCarrier(request.getHeaders()));

    final Scope scope = tracer.activateSpan(span);
    final Context context = new Context();
    contextHolder.set(context);
    context.scope = scope;
    context.span = span;
  }

  public static void exit(final Object response, final Throwable thrown) {
    final Context context = contextHolder.get();
    if (context == null)
      return;

    if (thrown != null) {
      captureException(context.span, thrown);
    }
    else {
      try {
        final ClientHttpResponse httpResponse = (ClientHttpResponse)response;
        Tags.HTTP_STATUS.set(context.span, httpResponse.getStatusCode().value());
      }
      catch (final Exception ignore) {
      }
    }

    context.scope.close();
    context.span.finish();
    contextHolder.remove();
  }

  static void captureException(final Span span, final Throwable t) {
    final Map<String,Object> exceptionLogs = new HashMap<>();
    exceptionLogs.put("event", Tags.ERROR.getKey());
    exceptionLogs.put("error.object", t);
    span.log(exceptionLogs);
    Tags.ERROR.set(span, true);
  }
}