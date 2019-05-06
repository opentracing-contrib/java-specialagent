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

import io.opentracing.Tracer;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.core.Ordered;
import org.springframework.http.server.PathContainer;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Tracing {@link WebFilter} for Spring WebFlux.
 *
 * Current server span context is accessible via {@link ServerWebExchange#getAttribute(String)} with name
 * {@link TracingWebFilter#SERVER_SPAN_CONTEXT}.
 *
 * Based on {@code TraceWebFilter} from spring-cloud-sleuth-core.
 *
 * @author Csaba Kos
 */
public class TracingWebFilter implements WebFilter, Ordered {
  private static final Log LOG = LogFactory.getLog(TracingWebFilter.class);

  /**
   * Used as a key of {@link ServerWebExchange#getAttributes()}} to inject server span context
   */
  static final String SERVER_SPAN_CONTEXT = TracingWebFilter.class.getName() + ".activeSpanContext";

  private final Tracer tracer;
  private final int order;
  @Nullable
  private final Pattern skipPattern;
  private final Set<PathPattern> urlPatterns;
  private final List<WebFluxSpanDecorator> spanDecorators;

  public TracingWebFilter(
      final Tracer tracer,
      final int order,
      final Pattern skipPattern,
      final List<String> urlPatterns,
      final List<WebFluxSpanDecorator> spanDecorators
  ) {
    this.tracer = tracer;
    this.order = order;
    this.skipPattern = (skipPattern != null && StringUtils.hasText(skipPattern.pattern())) ? skipPattern : null;
    final PathPatternParser pathPatternParser = new PathPatternParser();
    this.urlPatterns = urlPatterns.stream().map(pathPatternParser::parse).collect(Collectors.toSet());
    this.spanDecorators = spanDecorators;
  }

  @Override
  public Mono<Void> filter(final ServerWebExchange exchange, final WebFilterChain chain) {
    final ServerHttpRequest request = exchange.getRequest();

    if (!shouldBeTraced(request)) {
      return chain.filter(exchange);
    }

    if (exchange.getAttribute(SERVER_SPAN_CONTEXT) != null) {
      if (LOG.isTraceEnabled()) {
        LOG.trace("Not tracing request " + request + " because it is already being traced");
      }
      return chain.filter(exchange);
    }

    return new TracingOperator(chain.filter(exchange), exchange, tracer, spanDecorators);
  }

  /**
   * It checks whether a request should be traced or not.
   *
   * @return whether request should be traced or not
   */
  boolean shouldBeTraced(final ServerHttpRequest request) {
    final PathContainer pathWithinApplication = request.getPath().pathWithinApplication();
    // skip URLs matching skip pattern
    // e.g. pattern is defined as '/health|/status' then URL 'http://localhost:5000/context/health' won't be traced
    if (skipPattern != null) {
      final String url = pathWithinApplication.value();
      if (skipPattern.matcher(url).matches()) {
        if (LOG.isTraceEnabled()) {
          LOG.trace("Not tracing request " + request + " because it matches skip pattern: " + skipPattern);
        }
        return false;
      }
    }
    if (!urlPatterns.isEmpty() && urlPatterns.stream().noneMatch(urlPattern -> urlPattern.matches(pathWithinApplication))) {
      if (LOG.isTraceEnabled()) {
        LOG.trace("Not tracing request " + request + " because it does not match any URL pattern: " + urlPatterns);
      }
      return false;
    }
    return true;
  }

  @Override
  public int getOrder() {
    return order;
  }
}

