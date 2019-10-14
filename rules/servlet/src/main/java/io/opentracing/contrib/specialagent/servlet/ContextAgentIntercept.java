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

package io.opentracing.contrib.specialagent.servlet;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import javax.servlet.Filter;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;

import io.opentracing.contrib.specialagent.AgentRuleUtil;
import io.opentracing.contrib.specialagent.Level;
import io.opentracing.contrib.specialagent.Logger;
import io.opentracing.contrib.web.servlet.filter.TracingFilter;

public abstract class ContextAgentIntercept extends ServletFilterAgentIntercept {
  public static final Logger logger = Logger.getLogger(ContextAgentIntercept.class);

  public static final String TRACING_FILTER_NAME = "tracingFilter";
  public static final String[] patterns = {"/*"};

  public static Object getAddFilterMethod(final ServletContext context) throws IllegalAccessException, InvocationTargetException, ServletException {
    if (servletContextToFilter.containsKey(context)) {
      if (logger.isLoggable(Level.FINER))
        logger.finer(">< ContextAgentIntercept#addFilter(" + AgentRuleUtil.getSimpleNameId(context) + "): hasFilter(context) == true");

      return null;
    }

    final Method addFilterMethod = getFilterMethod(context);
    if (addFilterMethod == null) {
      if (logger.isLoggable(Level.FINER))
        logger.finer(">< ContextAgentIntercept#addFilter(" + AgentRuleUtil.getSimpleNameId(context) + "): ServletContext#addFilter(String,Filter) is missing");

      return null;
    }

    final TracingFilter tracingFilter = getFilter(context, false);
    // If the tracingFilter instance is a TracingProxyFilter, then it was
    // created with ServletFilterAgentIntercept#getProxyFilter. This should
    // never happen, because ServletContext#addFilter happens first in the
    // servlet lifecycle.
    if (tracingFilter instanceof TracingProxyFilter) {
      if (logger.isLoggable(Level.FINER))
        logger.finer(">< ContextAgentIntercept#addFilter(" + AgentRuleUtil.getSimpleNameId(context) + "): tracingFilter instanceof TracingProxyFilter");

      return null;
    }

    if (logger.isLoggable(Level.FINER))
      logger.finer(">> ContextAgentIntercept#addFilter(" + AgentRuleUtil.getSimpleNameId(context) + "): ServletContext#addFilter(\"" + TRACING_FILTER_NAME + "\"," + AgentRuleUtil.getSimpleNameId(tracingFilter) + ")");

    return addFilterMethod.invoke(context, TRACING_FILTER_NAME, tracingFilter);
  }

  public static boolean invoke(final Object[] returned, final Object obj, final Method method, final Object ... args) {
    if (method == null)
      return false;

    try {
      returned[0] = method.invoke(obj, args);
      return true;
    }
    catch (final IllegalAccessException | InvocationTargetException e) {
      return false;
    }
  }

  public static Method getFilterMethod(final ServletContext context) {
    return getMethod(context.getClass(), "addFilter", String.class, Filter.class);
  }
}