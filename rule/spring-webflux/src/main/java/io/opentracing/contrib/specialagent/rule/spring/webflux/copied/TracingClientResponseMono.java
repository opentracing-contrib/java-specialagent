/*
 * Copyright 2013-2019 the original author or authors. Copyright 2019 The OpenTracing Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.opentracing.contrib.specialagent.rule.spring.webflux.copied;

import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;
import io.opentracing.tag.Tags;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.CoreSubscriber;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.util.List;

/**
 * Similar to {@code MonoWebClientTrace} from spring-cloud-sleuth-core.
 *
 * @author Csaba Kos
 */
class TracingClientResponseMono extends Mono<ClientResponse> {
  private final ClientRequest request;
  private final ExchangeFunction next;
  private final Tracer tracer;
  private final List<WebClientSpanDecorator> spanDecorators;

  TracingClientResponseMono(
      final ClientRequest clientRequest,
      final ExchangeFunction next,
      final Tracer tracer,
      final List<WebClientSpanDecorator> spanDecorators
  ) {
    this.request = clientRequest;
    this.next = next;
    this.tracer = tracer;
    this.spanDecorators = spanDecorators;
  }

  @Override
  public void subscribe(final CoreSubscriber<? super ClientResponse> subscriber) {
    final Context context = subscriber.currentContext();
    final Span parentSpan = context.<Span>getOrEmpty(Span.class).orElseGet(tracer::activeSpan);

    final Span span = tracer.buildSpan(request.method().toString())
        .asChildOf(parentSpan)
        .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT)
        .start();

    try (final Scope scope = tracer.scopeManager().activate(span, false)) {
      final ClientRequest.Builder requestBuilder = ClientRequest.from(request);
      requestBuilder.headers(httpHeaders ->
          tracer.inject(span.context(), Format.Builtin.HTTP_HEADERS, new HttpHeadersCarrier(httpHeaders)));
      final ClientRequest mutatedRequest = requestBuilder.build();

      next.exchange(mutatedRequest).subscribe(
          new TracingClientResponseSubscriber(subscriber, mutatedRequest, context, span, spanDecorators)
      );
    }
  }
}

