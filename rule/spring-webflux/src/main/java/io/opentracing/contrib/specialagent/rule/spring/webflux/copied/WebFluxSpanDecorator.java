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
package io.opentracing.contrib.specialagent.rule.spring.webflux.copied;

import io.opentracing.Span;
import io.opentracing.tag.Tags;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.server.ServerWebExchange;

import java.net.Inet6Address;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeoutException;

/**
 * SpanDecorator to decorate span at different stages in WebFlux processing (at subscription, after error or completion).
 *
 * @author Csaba Kos
 */
public interface WebFluxSpanDecorator {

  /**
   * Decorate span before {@code .onSubscribe()} is called. This is called right after span in created. Span
   * context is already present in request attributes with name {@link TracingWebFilter#SERVER_SPAN_CONTEXT}.
   *
   * @param exchange web exchange
   * @param span span to decorate
   */
  void onRequest(ServerWebExchange exchange, Span span);

  /**
   * Decorate span after successful completion.
   *
   * @param exchange web exchange
   * @param span span to decorate
   */
  void onResponse(ServerWebExchange exchange, Span span);

  /**
   * Decorate span after completion in error.
   *
   * @param exchange web exchange
   * @param exception exception
   * @param span span to decorate
   */
  void onError(ServerWebExchange exchange, Throwable exception, Span span);

  /**
   * Adds standard tags to span. {@link Tags#HTTP_URL}, {@link Tags#HTTP_STATUS}, {@link Tags#HTTP_METHOD} and
   * {@link Tags#COMPONENT}. In case of completion in error, tag {@link Tags#ERROR} is added and
   * {@link Tags#HTTP_STATUS} not because at this point it is not known.
   */
  class StandardTags implements WebFluxSpanDecorator {
    static final String COMPONENT_NAME = "java-spring-webflux";

    @Override
    public void onRequest(final ServerWebExchange exchange, final Span span) {
      Tags.COMPONENT.set(span, COMPONENT_NAME);
      final ServerHttpRequest request = exchange.getRequest();
      Tags.HTTP_METHOD.set(span, request.getMethodValue());
      Tags.HTTP_URL.set(span, request.getURI().toString());
      Optional.ofNullable(request.getRemoteAddress()).ifPresent(remoteAddress -> {
        Tags.PEER_HOSTNAME.set(span, remoteAddress.getHostString());
        Tags.PEER_PORT.set(span, remoteAddress.getPort());
        Optional.ofNullable(remoteAddress.getAddress()).ifPresent(
            inetAddress -> {
              if (inetAddress instanceof Inet6Address) {
                Tags.PEER_HOST_IPV6.set(span, inetAddress.getHostAddress());
              } else {
                Tags.PEER_HOST_IPV4.set(span, inetAddress.getHostAddress());
              }
            }
        );
      });
    }

    @Override
    public void onResponse(final ServerWebExchange exchange, final Span span) {
      Optional.ofNullable(exchange.getResponse().getStatusCode())
          .ifPresent(httpStatus -> Tags.HTTP_STATUS.set(span, httpStatus.value()));
    }

    @Override
    public void onError(final ServerWebExchange exchange, final Throwable exception, final Span span) {
      Tags.ERROR.set(span, Boolean.TRUE);
      span.log(logsForException(exception));
    }

    private Map<String, Object> logsForException(final Throwable throwable) {
      final Map<String, Object> errorLogs = new HashMap<>(2);
      errorLogs.put("event", throwable instanceof TimeoutException ? "timed out" : Tags.ERROR.getKey());
      errorLogs.put("error.object", throwable);
      return errorLogs;
    }
  }

  /**
   * Adds tags from WebFlux handler to span.
   */
  class WebFluxTags implements WebFluxSpanDecorator {
    @Override
    public void onRequest(final ServerWebExchange exchange, final Span span) {
      // No-op
    }

    @Override
    public void onResponse(final ServerWebExchange exchange, final Span span) {
      addWebFluxTags(exchange, span);
    }

    @Override
    public void onError(final ServerWebExchange exchange, final Throwable exception, final Span span) {
      addWebFluxTags(exchange, span);
    }

    private void addWebFluxTags(final ServerWebExchange exchange, final Span span) {
      final Object handler = exchange.getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE);
      if (handler == null) {
        return;
      }

      final Map<String, Object> logs = new HashMap<>(4);
      logs.put("event", "handle");

      final Object pattern = exchange.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
      final String patternAsString = pattern == null ? null : pattern.toString();
      if (pattern != null) {
        logs.put("handler", patternAsString);
      }

      if (handler instanceof HandlerMethod) {
        final HandlerMethod handlerMethod = (HandlerMethod) handler;
        final String methodName = handlerMethod.getMethod().getName();
        logs.put("handler.method_name", handlerMethod.getMethod().getName());
        span.setOperationName(methodName);
        logs.put("handler.class_simple_name", handlerMethod.getBeanType().getSimpleName());
      } else {
        if (pattern != null) {
          span.setOperationName(patternAsString);
        }
        logs.put("handler.class_simple_name", handler.getClass().getSimpleName());
      }
      span.log(logs);
    }
  }
}

