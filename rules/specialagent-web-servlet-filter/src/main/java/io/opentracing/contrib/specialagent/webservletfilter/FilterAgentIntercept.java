/* Copyright 2019 The OpenTracing Authors
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

package io.opentracing.contrib.specialagent.webservletfilter;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.logging.Level;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletResponse;

import io.opentracing.contrib.specialagent.AgentRule;
import io.opentracing.contrib.specialagent.AgentRuleUtil;
import io.opentracing.contrib.specialagent.EarlyReturnException;
import io.opentracing.contrib.web.servlet.filter.TracingFilter;
import io.opentracing.util.GlobalTracer;

public class FilterAgentIntercept {
  public static final Map<Filter,ServletContext> filterToServletContext = new WeakHashMap<>();
  public static final Map<ServletContext,TracingFilter> servletContextToFilter = new WeakHashMap<>();
  public static final Map<ServletRequest,Boolean> servletRequestToState = new WeakHashMap<>();
  public static final Map<ServletResponse,Integer> servletResponseToStatus = new WeakHashMap<>();

  public static TracingFilter getFilter(final ServletContext servletContext) throws ServletException {
    Objects.requireNonNull(servletContext);
    TracingFilter filter = servletContextToFilter.get(servletContext);
    if (filter != null)
      return filter;

    synchronized (servletContextToFilter) {
      filter = servletContextToFilter.get(servletContext);
      if (filter != null)
        return filter;

      servletContextToFilter.put(servletContext, filter = new TracingProxyFilter(GlobalTracer.get(), servletContext));
      return filter;
    }
  }

  public static final FilterChain noopFilterChain = new FilterChain() {
    @Override
    public void doFilter(final ServletRequest request, final ServletResponse response) throws IOException, ServletException {
    }
  };

  public static void service(final Object thiz, final Object req, final Object res) {
    // If `servletRequestToState` contains the request key, then this request
    // has been handled by doFilter
    if (servletRequestToState.remove((ServletRequest)req) != null)
      return;

    try {
      final HttpServlet servlet = (HttpServlet)thiz;
      final TracingFilter filter = getFilter(servlet.getServletContext());
      if (AgentRule.logger.isLoggable(Level.FINEST))
        AgentRule.logger.finest(">> TracingFilter#service(" + AgentRuleUtil.getSimpleNameId(req) + ", " + AgentRuleUtil.getSimpleNameId(res) +  ")");

      filter.doFilter((ServletRequest)req, (ServletResponse)res, noopFilterChain);
      if (AgentRule.logger.isLoggable(Level.FINEST))
        AgentRule.logger.finest("<< TracingFilter#service(" + AgentRuleUtil.getSimpleNameId(req) + ", " + AgentRuleUtil.getSimpleNameId(res) +  ")");
    }
    catch (final Exception e) {
      AgentRule.logger.log(Level.WARNING, e.getMessage(), e);
    }
  }

  public static void init(final Object thiz, final Object filterConfig) {
    filterToServletContext.put((Filter)thiz, ((FilterConfig)filterConfig).getServletContext());
  }

  public static void doFilter(final Object thiz, final Object req, final Object res, final Object chain) {
    if (thiz instanceof TracingFilter)
      return;

    final ServletRequest request = (ServletRequest)req;
    if (servletRequestToState.containsKey(request))
      return;

    try {
      final Filter filter = (Filter)thiz;
      final ServletContext servletContext = request.getServletContext() != null ? request.getServletContext() : filterToServletContext.get(filter);
      final TracingFilter tracingFilter = getFilter(servletContext);
      servletRequestToState.put(request, Boolean.TRUE);
      if (AgentRule.logger.isLoggable(Level.FINEST))
        AgentRule.logger.finest(">> TracingFilter#doFilter(" + AgentRuleUtil.getSimpleNameId(request) + ", " + AgentRuleUtil.getSimpleNameId(res) +  ")");

      tracingFilter.doFilter(request, (ServletResponse)res, new FilterChain() {
        @Override
        public void doFilter(final ServletRequest request, final ServletResponse response) throws IOException, ServletException {
          filter.doFilter(request, response, (FilterChain)chain);
          if (AgentRule.logger.isLoggable(Level.FINEST))
            AgentRule.logger.finest("<< TracingFilter#doFilter(" + AgentRuleUtil.getSimpleNameId(request) + ", " + AgentRuleUtil.getSimpleNameId(response) +  ")");
        }
      });
    }
    catch (final Exception e) {
      AgentRule.logger.log(Level.WARNING, e.getMessage(), e);
      return;
    }

    throw new EarlyReturnException();
  }

  public static void response(final Object thiz, final int status) {
    servletResponseToStatus.put((ServletResponse)thiz, status);
  }

  public static int getSatusCode(final ServletResponse response) {
    final Integer statusCode = servletResponseToStatus.get(response);
    return statusCode != null ? statusCode : HttpServletResponse.SC_OK;
  }
}