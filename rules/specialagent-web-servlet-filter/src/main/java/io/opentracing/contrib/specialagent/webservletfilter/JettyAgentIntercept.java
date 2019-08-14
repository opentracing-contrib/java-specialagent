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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.EnumSet;

import javax.servlet.DispatcherType;
import javax.servlet.ServletContext;

import io.opentracing.contrib.specialagent.AgentRuleUtil;
import io.opentracing.contrib.specialagent.Level;

public class JettyAgentIntercept extends ContextAgentIntercept {
  public static void addFilter(final Object thiz) {
    ServletContext context = null;
    try {
      context = (ServletContext)thiz.getClass().getMethod("getServletContext").invoke(thiz);
      final Object registration = getAddFilterMethod(context);
      if (registration != null) {
        final Method addMappingForUrlPatternsMethod = registration.getClass().getMethod("addMappingForUrlPatterns", EnumSet.class, boolean.class, String[].class);
        addMappingForUrlPatternsMethod.invoke(registration, EnumSet.allOf(DispatcherType.class), true, patterns);
      }
    }
    catch (final IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
      logger.log(Level.WARNING, e.getMessage(), e);
      if (context != null)
        servletContextToFilter.remove(context);
    }

    if (logger.isLoggable(Level.FINER))
      logger.finer("<< JettyAgentIntercept#addFilter(" + AgentRuleUtil.getSimpleNameId(thiz) + ")");
  }
}