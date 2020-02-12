/* Copyright 2018 The OpenTracing Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentracing.contrib.specialagent.rule.servlet;

import java.util.List;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.contrib.specialagent.AgentRuleUtil;
import io.opentracing.contrib.specialagent.Level;
import io.opentracing.contrib.specialagent.rule.servlet.ext.TracingFilterUtil;
import io.opentracing.contrib.specialagent.rule.servlet.ext.TracingProxyFilter;
import io.opentracing.contrib.web.servlet.filter.ServletFilterSpanDecorator;
import io.opentracing.contrib.web.servlet.filter.TracingFilter;
import io.opentracing.util.GlobalTracer;

public class ServletAgentIntercept extends ServletFilterAgentIntercept {
  private static class Context {
    private Scope scope;
    private Span span;
  }

  private static final ThreadLocal<Context> contextHolder = new ThreadLocal<>();
  private static final List<ServletFilterSpanDecorator> spanDecorators = Configuration.spanDecorators;

  public static void init(final Object thiz, final Object servletConfig) {
    filterOrServletToServletContext.put(thiz, ((ServletConfig)servletConfig).getServletContext());
  }

  private static ServletContext getServletContext(final HttpServlet servlet) {
    final ServletConfig servletConfig = servlet.getServletConfig();
    ServletContext context;
    if (servletConfig == null || (context = servletConfig.getServletContext()) == null)
      context = filterOrServletToServletContext.get(servlet);

    if (context == null)
      logger.log(Level.WARNING, "Could not get context for: " + servlet);

    return context;
  }

  public static void serviceEnter(final Object thiz, final Object req, final Object res) {
    try {
      final ServletContext context = getServletContext((HttpServlet)thiz);
      if (context == null)
        return;

      final TracingFilter tracingFilter = getFilter(context, true);

      // If the tracingFilter instance is not a TracingProxyFilter, then it was
      // created with ServletContext#addFilter. Therefore, the intercept of the
      // Filter#doFilter method is not necessary.
      if (!(tracingFilter instanceof TracingProxyFilter))
        return;

      // If `servletRequestToState` contains the request key, then this request
      // has been handled by doFilter
      final HttpServletRequest request = (HttpServletRequest)req;
      if (request.getAttribute(TracingFilter.SERVER_SPAN_CONTEXT) != null)
        return;

      Context spanContext = contextHolder.get();
      if (spanContext != null)
        return;

      if (!Configuration.isTraced(request))
        return;

      final Tracer tracer = GlobalTracer.get();
      spanContext = new Context();
      contextHolder.set(spanContext);

      final Span span = TracingFilterUtil.buildSpan(request, tracer, spanDecorators);
      spanContext.span = span;
      spanContext.scope = tracer.activateSpan(span);
      if (logger.isLoggable(Level.FINER))
        logger.finer("<< ServletAgentIntercept#service(" + AgentRuleUtil.getSimpleNameId(req) + "," + AgentRuleUtil.getSimpleNameId(res) + "," + AgentRuleUtil.getSimpleNameId(context) + ")");
    }
    catch (final Exception e) {
      logger.log(Level.WARNING, e.getMessage(), e);
    }
  }

  public static void serviceExit(final Object request, final Object response, final Throwable thrown) {
    try {
      final Context spanContext = contextHolder.get();
      if (spanContext == null)
        return;

      final HttpServletRequest httpRequest = (HttpServletRequest)request;
      final HttpServletResponse httpResponse = (HttpServletResponse)response;

      if (thrown != null)
        TracingFilterUtil.onError(httpRequest, httpResponse, thrown, spanContext.span, spanDecorators);
      else
        TracingFilterUtil.onResponse(httpRequest, httpResponse, spanContext.span, spanDecorators);

      spanContext.scope.close();
      spanContext.span.finish();
      contextHolder.remove();
    }
    catch (final Exception e) {
      logger.log(Level.WARNING, e.getMessage(), e);
    }
  }
}