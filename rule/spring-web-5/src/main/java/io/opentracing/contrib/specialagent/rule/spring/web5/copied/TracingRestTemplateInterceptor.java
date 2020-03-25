/**
 * Copyright 2016-2019 The OpenTracing Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.opentracing.contrib.specialagent.rule.spring.web5.copied;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;

/**
 * OpenTracing Spring RestTemplate integration.
 * This interceptor creates tracing data for all outgoing requests.
 *
 * @author Pavol Loffay
 */
public class TracingRestTemplateInterceptor implements ClientHttpRequestInterceptor {
  private static final Log log = LogFactory.getLog(
      TracingRestTemplateInterceptor.class);

  private Tracer tracer;
  private List<RestTemplateSpanDecorator> spanDecorators;

  public TracingRestTemplateInterceptor() {
    this(GlobalTracer.get(), Collections.<RestTemplateSpanDecorator>singletonList(
        new RestTemplateSpanDecorator.StandardTags()));
  }

  /**
   * @param tracer tracer
   */
  public TracingRestTemplateInterceptor(Tracer tracer) {
    this(tracer, Collections.<RestTemplateSpanDecorator>singletonList(
        new RestTemplateSpanDecorator.StandardTags()));
  }

  /**
   * @param tracer tracer
   * @param spanDecorators list of decorators
   */
  public TracingRestTemplateInterceptor(Tracer tracer, List<RestTemplateSpanDecorator> spanDecorators) {
    this.tracer = tracer;
    this.spanDecorators = new ArrayList<>(spanDecorators);
  }

  @Override
  public ClientHttpResponse intercept(HttpRequest httpRequest, byte[] body,
      ClientHttpRequestExecution execution) throws IOException {
    ClientHttpResponse httpResponse;

    Span span = tracer.buildSpan(httpRequest.getMethod().toString())
        .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT)
        .start();
    tracer.inject(span.context(), Format.Builtin.HTTP_HEADERS,
        new HttpHeadersCarrier(httpRequest.getHeaders()));

    for (RestTemplateSpanDecorator spanDecorator : spanDecorators) {
      try {
        spanDecorator.onRequest(httpRequest, span);
      } catch (RuntimeException exDecorator) {
        log.error("Exception during decorating span", exDecorator);
      }
    }

    try (Scope scope = tracer.activateSpan(span)) {
      httpResponse = execution.execute(httpRequest, body);
      for (RestTemplateSpanDecorator spanDecorator : spanDecorators) {
        try {
          spanDecorator.onResponse(httpRequest, httpResponse, span);
        } catch (RuntimeException exDecorator) {
          log.error("Exception during decorating span", exDecorator);
        }
      }
    } catch (Exception ex) {
      for (RestTemplateSpanDecorator spanDecorator : spanDecorators) {
        try {
          spanDecorator.onError(httpRequest, ex, span);
        } catch (RuntimeException exDecorator) {
          log.error("Exception during decorating span", exDecorator);
        }
      }
      throw ex;
    } finally {
      span.finish();
    }

    return httpResponse;
  }
}
