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

import io.opentracing.Tracer;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Similar to {@code TraceExchangeFilterFunction} from spring-cloud-sleuth-core.
 *
 * @author Csaba Kos
 */
public class TracingExchangeFilterFunction implements ExchangeFilterFunction {
  private final Tracer tracer;
  private final List<WebClientSpanDecorator> spanDecorators;

  public TracingExchangeFilterFunction(final Tracer tracer, final List<WebClientSpanDecorator> spanDecorators) {
    this.tracer = tracer;
    this.spanDecorators = spanDecorators;
  }

  @Override
  public Mono<ClientResponse> filter(final ClientRequest clientRequest, final ExchangeFunction next) {
    return new TracingClientResponseMono(clientRequest, next, tracer, spanDecorators);
  }
}
