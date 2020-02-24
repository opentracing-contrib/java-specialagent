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

package io.opentracing.contrib.specialagent.rule.spring.web3;

import io.opentracing.contrib.specialagent.LocalSpanContext;
import io.opentracing.contrib.specialagent.SpanUtil;

import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpResponse;

import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.contrib.specialagent.rule.spring.web3.copied.HttpHeadersCarrier;
import io.opentracing.propagation.Format.Builtin;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;

public class SpringWebAgentIntercept {
  private static final ThreadLocal<LocalSpanContext> contextHolder = new ThreadLocal<>();

  public static void enter(final Object thiz) {
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
    final LocalSpanContext context = new LocalSpanContext(span, scope);
    contextHolder.set(context);
  }

  public static void exit(final Object response, final Throwable thrown) {
    final LocalSpanContext context = contextHolder.get();
    if (context == null)
      return;

    if (thrown != null) {
      SpanUtil.onError(thrown, context.getSpan());
    }
    else {
      try {
        final ClientHttpResponse httpResponse = (ClientHttpResponse)response;
        Tags.HTTP_STATUS.set(context.getSpan(), httpResponse.getStatusCode().value());
      }
      catch (final Exception ignore) {
      }
    }

    context.closeAndFinish();
    contextHolder.remove();
  }

}