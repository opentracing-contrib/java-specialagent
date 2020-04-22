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
package io.opentracing.contrib.specialagent.rule.spring.webmvc.copied;

import io.opentracing.Span;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * SpanDecorator to decorate span at different stages in filter processing.
 *
 * @author Pavol Loffay
 */
public interface HandlerInterceptorSpanDecorator {

  /**
   * This is called in
   * {@link org.springframework.web.servlet.HandlerInterceptor#preHandle(HttpServletRequest, HttpServletResponse, Object)}.
   *
   * @param httpServletRequest request
   * @param handler handler
   * @param span current span
   */
  void onPreHandle(HttpServletRequest httpServletRequest, Object handler, Span span);

  /**
   * This is called in
   * {@link org.springframework.web.servlet.HandlerInterceptor#afterCompletion(HttpServletRequest, HttpServletResponse, Object, Exception)}
   *
   * @param httpServletRequest request
   * @param httpServletResponse response
   * @param handler handler
   * @param ex exception
   * @param span current span
   */
  void onAfterCompletion(HttpServletRequest httpServletRequest,
      HttpServletResponse httpServletResponse, Object handler,
      Exception ex, Span span);

  /**
   * For spring-webmvc 4+
   * This is called in
   * {@link org.springframework.web.servlet.AsyncHandlerInterceptor#afterConcurrentHandlingStarted(HttpServletRequest, HttpServletResponse, Object)}
   *
   * @param httpServletRequest request
   * @param httpServletResponse response
   * @param handler handler
   * @param span current span
   */
  void onAfterConcurrentHandlingStarted(HttpServletRequest httpServletRequest,
      HttpServletResponse httpServletResponse, Object handler,
      Span span);

  /**
   * Decorator to record details about the handler as log events recorded on the span.
   */
  HandlerInterceptorSpanDecorator STANDARD_LOGS = new HandlerInterceptorSpanDecorator() {

    @Override
    public void onPreHandle(HttpServletRequest httpServletRequest, Object handler, Span span) {
      Map<String, Object> logs = new HashMap<>(3);
      logs.put("event", "preHandle");
      logs.put(HandlerUtils.HANDLER, handler);

      String metaData = HandlerUtils.className(handler);
      if (metaData != null) {
        logs.put(HandlerUtils.HANDLER_CLASS_NAME, metaData);
      }

      metaData = HandlerUtils.methodName(handler);
      if (metaData != null) {
        logs.put(HandlerUtils.HANDLER_METHOD_NAME, metaData);
      }

      span.log(logs);
    }

    @Override
    public void onAfterCompletion(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse,
        Object handler, Exception ex, Span span) {
      Map<String, Object> logs = new HashMap<>(2);
      logs.put("event", "afterCompletion");
      logs.put(HandlerUtils.HANDLER, handler);
      span.log(logs);
    }

    @Override
    public void onAfterConcurrentHandlingStarted(HttpServletRequest httpServletRequest,
        HttpServletResponse httpServletResponse, Object handler, Span span) {
      Map<String, Object> logs = new HashMap<>(2);
      logs.put("event", "afterConcurrentHandlingStarted");
      logs.put(HandlerUtils.HANDLER, handler);
      span.log(logs);
    }
  };

  /**
   * Use the handler's method name as the span's operation name.
   */
  HandlerInterceptorSpanDecorator HANDLER_METHOD_OPERATION_NAME = new HandlerInterceptorSpanDecorator() {

    @Override
    public void onPreHandle(HttpServletRequest httpServletRequest, Object handler, Span span) {
      String metaData = HandlerUtils.methodName(handler);
      if (metaData != null) {
        span.setOperationName(metaData);
      }
    }

    @Override
    public void onAfterCompletion(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse,
        Object handler, Exception ex, Span span) {
    }

    @Override
    public void onAfterConcurrentHandlingStarted(HttpServletRequest httpServletRequest,
        HttpServletResponse httpServletResponse, Object handler, Span span) {
    }
  };

  /**
   * Helper class for deriving tags/logs from handler object.
   */
  class HandlerUtils {
    private HandlerUtils() {}

    /**
     * Class name of a handler serving request
     */
    public static final String HANDLER_CLASS_NAME = "handler.class_simple_name";
    /**
     * Method name of handler serving request
     */
    public static final String HANDLER_METHOD_NAME = "handler.method_name";
    /**
     * Spring handler object
     */
    public static final String HANDLER = "handler";

    public static String className(Object handler) {
      // org.springframework.web.method.HandlerMethod

      try {
        Class<?> clazz = (Class<?>) handler.getClass().getMethod("getBeanType").invoke(handler);
        return clazz.getSimpleName();
      } catch (Exception ignore) {
      }

      return handler.getClass().getSimpleName();
    }

    public static String methodName(Object handler) {
      try {
        final Method method = (Method) handler.getClass().getMethod("getMethod").invoke(handler);
        return method.getName();
      } catch (Exception ignore) {
      }

      return null;
    }
  }
}
