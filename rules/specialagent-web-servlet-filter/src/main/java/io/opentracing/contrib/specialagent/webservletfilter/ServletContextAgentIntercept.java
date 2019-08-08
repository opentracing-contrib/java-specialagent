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

import java.lang.reflect.Method;
import java.util.EnumSet;

import javax.servlet.DispatcherType;
import javax.servlet.FilterRegistration;
import javax.servlet.ServletContext;

import io.opentracing.contrib.specialagent.AgentRuleUtil;
import io.opentracing.contrib.specialagent.Level;
import io.opentracing.contrib.specialagent.Logger;
import io.opentracing.contrib.web.servlet.filter.TracingFilter;
import io.opentracing.util.GlobalTracer;

public class ServletContextAgentIntercept extends ContextAgentIntercept {
  public static final Logger logger = Logger.getLogger(ServletContextAgentIntercept.class);

  public static void addFilter(final Object thiz) {
    if (!(thiz instanceof ServletContext))
      return;

    final ServletContext context = (ServletContext)thiz;
    if (logger.isLoggable(Level.FINER))
      logger.finer(">> ServletContextAgentIntercept#addFilter(" + AgentRuleUtil.getSimpleNameId(context) + ")");

    final Method addFilterMethod = getFilterMethod(context);
    if (addFilterMethod == null) {
      if (logger.isLoggable(Level.FINER))
        logger.finer("<< ServletContextAgentIntercept#addFilter(" + AgentRuleUtil.getSimpleNameId(context) + "): isFilterMethodPresent = false");

      return;
    }

    try {
      final TracingFilter filter = new TracingFilter(GlobalTracer.get());
      final FilterRegistration.Dynamic registration = (FilterRegistration.Dynamic)addFilterMethod.invoke(context, TRACING_FILTER_NAME, filter);
      if (registration != null)
        registration.addMappingForUrlPatterns(EnumSet.allOf(DispatcherType.class), true, patterns);
    }
    catch (final Exception e) {
      logger.log(Level.WARNING, e.getMessage(), e);
    }

    if (logger.isLoggable(Level.FINER))
      logger.finer("<< ServletContextAgentIntercept#addFilter(" + AgentRuleUtil.getSimpleNameId(context) + ")");
  }
}