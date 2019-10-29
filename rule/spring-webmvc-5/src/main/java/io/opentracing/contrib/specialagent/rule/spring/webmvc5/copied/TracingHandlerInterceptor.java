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
package io.opentracing.contrib.specialagent.rule.spring.webmvc5.copied;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

import io.opentracing.References;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;

/**
 * Tracing handler interceptor for spring web. It creates a new span for an incoming request
 * if there is no active request and a separate span for Spring's exception handling.
 * This handler depends on {@link TracingFilter}. Both classes have to be properly configured.
 *
 * <p>HTTP tags and logged errors are added in {@link TracingFilter}. This interceptor adds only
 * spring related logs (handler class/method).
 *
 * @author Pavol Loffay
 */
public class TracingHandlerInterceptor extends HandlerInterceptorAdapter {
  public static final String SERVER_SPAN_CONTEXT = "io.opentracing.contrib.web.servlet.filter.TracingFilter.activeSpanContext";

  private static final String SCOPE_STACK = TracingHandlerInterceptor.class.getName() + ".scopeStack";
  private static final String CONTINUATION_FROM_ASYNC_STARTED = TracingHandlerInterceptor.class.getName() + ".continuation";
  private static final String IS_ERROR_HANDLING_SPAN = TracingHandlerInterceptor.class.getName() + ".error_handling_span";

  private Tracer tracer;
  private List<HandlerInterceptorSpanDecorator> decorators;

  /**
   * @param tracer
   */
  public TracingHandlerInterceptor(Tracer tracer) {
    this(tracer, Arrays.asList(HandlerInterceptorSpanDecorator.STANDARD_LOGS,
        HandlerInterceptorSpanDecorator.HANDLER_METHOD_OPERATION_NAME));
  }

  /**
   * @param tracer tracer
   * @param decorators span decorators
   */
  public TracingHandlerInterceptor(Tracer tracer, List<HandlerInterceptorSpanDecorator> decorators) {
    this.tracer = tracer;
    this.decorators = new ArrayList<>(decorators);
  }

  /**
   * This method determines whether the HTTP request is being traced.
   *
   * @param httpServletRequest The HTTP request
   * @return Whether the request is being traced
   */
  static boolean isTraced(HttpServletRequest httpServletRequest) {
    // exclude pattern, span is not started in filter
    return httpServletRequest.getAttribute(SERVER_SPAN_CONTEXT) instanceof SpanContext;
  }

  @Override
  public boolean preHandle(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, Object handler) {
    Deque<Scope> activeSpanStack = getScopeStack(httpServletRequest);

    if (!isTraced(httpServletRequest)) {
      return true;
    }

    /*
     * 1. check if there is an active span, it has been activated in servlet filter or in this interceptor (forward)
     * 2. if there is no active span then it can be handling of an async request or spring boot default error handling
     */
    Span serverSpan = tracer.activeSpan();
    if (serverSpan == null) {
      if (httpServletRequest.getAttribute(CONTINUATION_FROM_ASYNC_STARTED) != null) {
        serverSpan = (Span) httpServletRequest.getAttribute(CONTINUATION_FROM_ASYNC_STARTED);
        httpServletRequest.removeAttribute(CONTINUATION_FROM_ASYNC_STARTED);
        activeSpanStack.push(tracer.activateSpan(serverSpan));
      } else {
        // spring boot default error handling, executes interceptor after processing in the filter (ugly huh?)
        serverSpan = tracer.buildSpan(httpServletRequest.getMethod())
            .addReference(References.FOLLOWS_FROM, (SpanContext) httpServletRequest.getAttribute(SERVER_SPAN_CONTEXT))
            .start();
        httpServletRequest.setAttribute(IS_ERROR_HANDLING_SPAN, true);
        activeSpanStack.push(tracer.activateSpan(serverSpan));
      }
    }

    for (HandlerInterceptorSpanDecorator decorator : decorators) {
      decorator.onPreHandle(httpServletRequest, handler, serverSpan);
    }

    return true;
  }

  @Override
  public void afterConcurrentHandlingStarted (
      HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, Object handler) {

    if (!isTraced(httpServletRequest)) {
      return;
    }

    Span span = tracer.activeSpan();
    for (HandlerInterceptorSpanDecorator decorator : decorators) {
      decorator.onAfterConcurrentHandlingStarted(httpServletRequest, httpServletResponse, handler, span);
    }
    httpServletRequest.setAttribute(CONTINUATION_FROM_ASYNC_STARTED, span);
  }


  @Override
  public void afterCompletion(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse,
      Object handler, Exception ex) {

    if (!isTraced(httpServletRequest)) {
      return;
    }

    Span span = tracer.activeSpan();
    for (HandlerInterceptorSpanDecorator decorator : decorators) {
      decorator.onAfterCompletion(httpServletRequest, httpServletResponse, handler, ex, span);
    }
    Deque<Scope> scopeStack = getScopeStack(httpServletRequest);
    if(scopeStack.size() > 0) {
      Scope scope = scopeStack.pop();
      scope.close();
    }
    if (httpServletRequest.getAttribute(IS_ERROR_HANDLING_SPAN) != null) {
      httpServletRequest.removeAttribute(IS_ERROR_HANDLING_SPAN);
      span.finish();
    }
  }

  private Deque<Scope> getScopeStack(HttpServletRequest request) {
    Deque<Scope> stack = (Deque<Scope>) request.getAttribute(SCOPE_STACK);
    if (stack == null) {
      stack = new ArrayDeque<>();
      request.setAttribute(SCOPE_STACK, stack);
    }
    return stack;
  }
}
