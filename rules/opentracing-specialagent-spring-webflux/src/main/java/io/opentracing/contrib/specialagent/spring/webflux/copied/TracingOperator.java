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
package io.opentracing.contrib.specialagent.spring.webflux.copied;

import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;
import io.opentracing.tag.Tags;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.CoreSubscriber;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoOperator;
import reactor.util.context.Context;

import java.util.List;

/**
 * Similar to {@code MonoWebFilterTrace} from spring-cloud-sleuth-core.
 *
 * @author Csaba Kos
 */
class TracingOperator extends MonoOperator<Void, Void> {
  private final Tracer tracer;
  private final ServerWebExchange exchange;
  private final List<WebFluxSpanDecorator> spanDecorators;

  TracingOperator(
      final Mono<? extends Void> source,
      final ServerWebExchange exchange,
      final Tracer tracer,
      final List<WebFluxSpanDecorator> spanDecorators
  ) {
    super(source);
    this.tracer = tracer;
    this.exchange = exchange;
    this.spanDecorators = spanDecorators;
  }

  @Override
  public void subscribe(final CoreSubscriber<? super Void> subscriber) {
    final Context context = subscriber.currentContext();
    final Span parentSpan = context.<Span>getOrEmpty(Span.class).orElseGet(tracer::activeSpan);
    final ServerHttpRequest request = exchange.getRequest();

    final SpanContext extractedContext;
    if (parentSpan != null) {
      extractedContext = parentSpan.context();
    } else {
      extractedContext = tracer.extract(Format.Builtin.HTTP_HEADERS, new HttpHeadersExtractAdapter(request.getHeaders()));
    }

    final Span span = tracer.buildSpan(request.getMethodValue())
        .asChildOf(extractedContext)
        .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_SERVER)
        .start();

    try (final Scope scope = tracer.scopeManager().activate(span, false)) {
      exchange.getAttributes().put(TracingWebFilter.SERVER_SPAN_CONTEXT, span.context());
      source.subscribe(new TracingSubscriber(subscriber, exchange, context, span, spanDecorators));
    }
  }
}

