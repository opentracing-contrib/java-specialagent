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

import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.reactivestreams.Subscription;
import org.springframework.web.server.ServerWebExchange;

import io.opentracing.Span;
import reactor.core.CoreSubscriber;
import reactor.util.context.Context;

/**
 * Similar to {@code WebFilterTraceSubscriber} from spring-could-sleuth-core.
 *
 * @author Csaba Kos
 */
class TracingSubscriber implements CoreSubscriber<Void> {
  private static final Log LOG = LogFactory.getLog(TracingSubscriber.class);

  private final CoreSubscriber<? super Void> subscriber;
  private final ServerWebExchange exchange;
  private final Context context;
  private final Span span;
  private final List<WebFluxSpanDecorator> spanDecorators;

  TracingSubscriber(
      final CoreSubscriber<? super Void> subscriber,
      final ServerWebExchange exchange,
      final Context context,
      final Span span,
      final List<WebFluxSpanDecorator> spanDecorators
  ) {
    this.subscriber = subscriber;
    this.exchange = exchange;
    this.context = context.put(Span.class, span);
    this.span = span;
    this.spanDecorators = spanDecorators;
  }

  @Override
  public void onSubscribe(final Subscription subscription) {
    spanDecorators.forEach(spanDecorator -> safelyCall(() -> spanDecorator.onRequest(exchange, span)));
    subscriber.onSubscribe(subscription);
  }

  @Override
  public void onNext(final Void aVoid) {
    // Never called
    subscriber.onNext(aVoid);
  }

  @Override
  public void onError(final Throwable throwable) {
    spanDecorators.forEach(spanDecorator -> safelyCall(() -> spanDecorator.onError(exchange, throwable, span)));
    span.finish();
    exchange.getAttributes().remove(TracingWebFilter.SERVER_SPAN_CONTEXT);
    subscriber.onError(throwable);
  }

  @Override
  public void onComplete() {
    spanDecorators.forEach(spanDecorator -> safelyCall(() -> spanDecorator.onResponse(exchange, span)));
    span.finish();
    exchange.getAttributes().remove(TracingWebFilter.SERVER_SPAN_CONTEXT);
    subscriber.onComplete();
  }

  @Override
  public Context currentContext() {
    return context;
  }

  private void safelyCall(final Runnable runnable) {
    try {
      runnable.run();
    } catch (final RuntimeException e) {
      LOG.error("Exception during decorating span", e);
    }
  }
}
