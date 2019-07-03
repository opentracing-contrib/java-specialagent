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

package io.opentracing.contrib.specialagent.webservletfilter;

import java.io.IOException;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.logging.Level;

import javax.servlet.FilterChain;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServlet;

import io.opentracing.contrib.specialagent.AgentRule;
import io.opentracing.contrib.web.servlet.filter.TracingFilter;
import io.opentracing.util.GlobalTracer;

public class HttpServletAgentIntercept {
  public static final Map<ServletContext,TracingFilter> servletContextToFilter = new WeakHashMap<>();
  public static final FilterChain noopFilterChain = new FilterChain() {
    @Override
    public void doFilter(final ServletRequest request, final ServletResponse response) throws IOException, ServletException {
    }
  };

  public static void enter(final Object thiz, final Object req, final Object res) {
    if (!(thiz instanceof HttpServlet))
      return;

    try {
      final HttpServlet servlet = (HttpServlet)thiz;
      final ServletContext servletContext = servlet.getServletContext();
      TracingFilter filter = servletContextToFilter.get(servletContext);
      if (filter == null) {
        synchronized (servletContextToFilter) {
          filter = servletContextToFilter.get(servletContext);
          if (filter == null) {
            servletContextToFilter.put(servletContext, filter = new TracingProxyFilter(GlobalTracer.get(), servlet.getServletContext()));
          }
        }
      }

      filter.doFilter((ServletRequest)req, (ServletResponse)res, noopFilterChain);
    }
    catch (final Exception e) {
      AgentRule.logger.log(Level.WARNING, e.getMessage(), e);
    }
  }
}