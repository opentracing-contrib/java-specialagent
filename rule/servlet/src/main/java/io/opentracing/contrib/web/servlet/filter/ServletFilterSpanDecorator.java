/*
 * Copyright 2016-2018 The OpenTracing Authors
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
package io.opentracing.contrib.web.servlet.filter;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.FilterChain;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import io.opentracing.Span;
import io.opentracing.contrib.specialagent.rule.servlet.FilterAgentIntercept;
import io.opentracing.tag.Tags;

/**
 * SpanDecorator to decorate span at different stages in filter processing (before filterChain.doFilter(), after and
 * if exception is thrown).
 *
 * @author Pavol Loffay
 */
public interface ServletFilterSpanDecorator {

    /**
     * Decorate span before {@link javax.servlet.Filter#doFilter(ServletRequest, ServletResponse, FilterChain)} is
     * called. This is called right after span in created. Span is already present in request attributes with name
     * {@link TracingFilter#SERVER_SPAN_CONTEXT}.
     *
     * @param httpServletRequest request
     * @param span span to decorate
     */
    void onRequest(HttpServletRequest httpServletRequest, Span span);

    /**
     * Decorate span after {@link javax.servlet.Filter#doFilter(ServletRequest, ServletResponse, FilterChain)}. When it
     * is an async request this will be called in {@link javax.servlet.AsyncListener#onComplete(javax.servlet.AsyncEvent)}.
     *
     * @param httpServletRequest request
     * @param httpServletResponse response
     * @param span span to decorate
     */
    void onResponse(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, Span span);

    /**
     * Decorate span when an exception is thrown during processing in
     * {@link javax.servlet.Filter#doFilter(ServletRequest, ServletResponse, FilterChain)}. This is
     * also called in {@link javax.servlet.AsyncListener#onError(javax.servlet.AsyncEvent)}.
     *
     * @param httpServletRequest request
     * @param exception exception
     * @param span span to decorate
     */
    void onError(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse,
                 Throwable exception, Span span);

    /**
     * Decorate span on asynchronous request timeout. It is called in
     * {@link javax.servlet.AsyncListener#onTimeout(javax.servlet.AsyncEvent)}.
     *
     * @param httpServletRequest request
     * @param httpServletResponse response
     * @param timeout timeout
     * @param span span to decorate
     */
    void onTimeout(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse,
                 long timeout, Span span);

    /**
     * Adds standard tags to span. {@link Tags#HTTP_URL}, {@link Tags#HTTP_STATUS}, {@link Tags#HTTP_METHOD} and
     * {@link Tags#COMPONENT}. If an exception during
     * {@link javax.servlet.Filter#doFilter(ServletRequest, ServletResponse, FilterChain)} is thrown tag
     * {@link Tags#ERROR} is added and {@link Tags#HTTP_STATUS} not because at this point it is not known.
     */
    ServletFilterSpanDecorator STANDARD_TAGS = new ServletFilterSpanDecorator() {
        @Override
        public void onRequest(HttpServletRequest httpServletRequest, Span span) {
            Tags.COMPONENT.set(span, "java-web-servlet");

            Tags.HTTP_METHOD.set(span, httpServletRequest.getMethod());
            //without query params
            Tags.HTTP_URL.set(span, httpServletRequest.getRequestURL().toString());
        }

        @Override
        public void onResponse(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse,
                Span span) {
                Tags.HTTP_STATUS.set(span, FilterAgentIntercept.getSatusCode(httpServletResponse));
        }

        @Override
        public void onError(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse,
                            Throwable exception, Span span) {
            Tags.ERROR.set(span, Boolean.TRUE);
            span.log(logsForException(exception));

            if (FilterAgentIntercept.getSatusCode(httpServletResponse) == HttpServletResponse.SC_OK) {
                // exception is thrown in filter chain, but status code is incorrect
                Tags.HTTP_STATUS.set(span, 500);
            }
        }

        @Override
        public void onTimeout(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse,
                              long timeout, Span span) {
            Map<String, Object> timeoutLogs = new HashMap<>(2);
            timeoutLogs.put("event", "timeout");
            timeoutLogs.put("timeout", timeout);
            span.log(timeoutLogs);
        }

        private Map<String, String> logsForException(Throwable throwable) {
            Map<String, String> errorLog = new HashMap<>(3);
            errorLog.put("event", Tags.ERROR.getKey());

            String message = throwable.getCause() != null ? throwable.getCause().getMessage() : throwable.getMessage();
            if (message != null) {
                errorLog.put("message", message);
            }
            StringWriter sw = new StringWriter();
            throwable.printStackTrace(new PrintWriter(sw));
            errorLog.put("stack", sw.toString());

            return errorLog;
        }
    };
}
