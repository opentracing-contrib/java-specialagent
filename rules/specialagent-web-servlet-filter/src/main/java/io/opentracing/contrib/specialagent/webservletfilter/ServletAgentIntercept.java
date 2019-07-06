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
import java.util.logging.Level;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServlet;

import io.opentracing.contrib.specialagent.AgentRule;
import io.opentracing.contrib.specialagent.AgentRuleUtil;
import io.opentracing.contrib.web.servlet.filter.TracingFilter;

public class ServletAgentIntercept extends ServletFilterAgentIntercept {
  public static final FilterChain noopFilterChain = new FilterChain() {
    @Override
    public void doFilter(final ServletRequest request, final ServletResponse response) throws IOException, ServletException {
    }
  };

  public static void service(final Object thiz, final Object req, final Object res) {
    try {
      final HttpServlet servlet = (HttpServlet)thiz;
      final TracingFilter tracingFilter = getFilter(servlet.getServletContext());

      // If the tracingFilter instance is not a TracingProxyFilter, then it was
      // created with ServletContext#addFilter. Therefore, the intercept of the
      // Filter#doFilter method is not necessary.
      if (!(tracingFilter instanceof TracingProxyFilter))
        return;

      // If `servletRequestToState` contains the request key, then this request
      // has been handled by doFilter
      if (servletRequestToState.remove((ServletRequest)req) != null)
        return;

      if (AgentRule.logger.isLoggable(Level.FINEST))
        AgentRule.logger.finest(">> ServletAgentIntercept#service(" + AgentRuleUtil.getSimpleNameId(req) + ", " + AgentRuleUtil.getSimpleNameId(res) +  ")");

      tracingFilter.doFilter((ServletRequest)req, (ServletResponse)res, noopFilterChain);
      if (AgentRule.logger.isLoggable(Level.FINEST))
        AgentRule.logger.finest("<< ServletAgentIntercept#service(" + AgentRuleUtil.getSimpleNameId(req) + ", " + AgentRuleUtil.getSimpleNameId(res) +  ")");
    }
    catch (final Exception e) {
      AgentRule.logger.log(Level.WARNING, e.getMessage(), e);
    }
  }
}