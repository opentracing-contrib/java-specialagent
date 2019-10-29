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

import io.opentracing.Span;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.reactivestreams.Subscription;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import reactor.core.CoreSubscriber;
import reactor.util.context.Context;

import java.util.List;

/**
 * Similar to {@code WebClientTracerSubscriber} from spring-cloud-sleuth-core.
 *
 * @author Csaba Kos
 */
class TracingClientResponseSubscriber implements CoreSubscriber<ClientResponse> {
  private static final Log LOG = LogFactory.getLog(TracingClientResponseSubscriber.class);

  private final CoreSubscriber<? super ClientResponse> subscriber;
  private final ClientRequest clientRequest;
  private final Context context;
  private final Span span;
  private final List<WebClientSpanDecorator> spanDecorators;

  TracingClientResponseSubscriber(
      final CoreSubscriber<? super ClientResponse> subscriber,
      final ClientRequest clientRequest,
      final Context context,
      final Span span,
      final List<WebClientSpanDecorator> spanDecorators
  ) {
    this.subscriber = subscriber;
    this.clientRequest = clientRequest;
    this.context = context.put(Span.class, span);
    this.span = span;
    this.spanDecorators = spanDecorators;
  }

  @Override
  public void onSubscribe(final Subscription subscription) {
    spanDecorators.forEach(spanDecorator -> safelyCall(() -> spanDecorator.onRequest(clientRequest, span)));

    subscriber.onSubscribe(new Subscription() {
      @Override
      public void request(final long n) {
        subscription.request(n);
      }

      @Override
      public void cancel() {
        spanDecorators.forEach(spanDecorator -> safelyCall(() -> spanDecorator.onCancel(clientRequest, span)));
        subscription.cancel();
        span.finish();
      }
    });
  }

  @Override
  public void onNext(final ClientResponse clientResponse) {
    try {
      // decorate response body
      subscriber.onNext(ClientResponse.from(clientResponse)
          .body(clientResponse.bodyToFlux(DataBuffer.class).subscriberContext(context))
          .build());
    } finally {
      spanDecorators.forEach(spanDecorator ->
          safelyCall(() -> spanDecorator.onResponse(clientRequest, clientResponse, span)));
    }
  }

  @Override
  public void onError(final Throwable throwable) {
    try {
      subscriber.onError(throwable);
    } finally {
      spanDecorators.forEach(spanDecorator -> safelyCall(() -> spanDecorator.onError(clientRequest, throwable, span)));
      span.finish();
    }
  }

  @Override
  public void onComplete() {
    try {
      subscriber.onComplete();
    } finally {
      span.finish();
    }
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

