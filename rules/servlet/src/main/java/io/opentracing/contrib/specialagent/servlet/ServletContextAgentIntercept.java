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

package io.opentracing.contrib.specialagent.servlet;

import java.util.EnumSet;

import javax.servlet.DispatcherType;
import javax.servlet.FilterRegistration;
import javax.servlet.ServletContext;

import io.opentracing.contrib.specialagent.AgentRuleUtil;
import io.opentracing.contrib.specialagent.Level;

public class ServletContextAgentIntercept extends ContextAgentIntercept {
  public static boolean addFilter(final Object thiz) {
    if (!(thiz instanceof ServletContext)) {
      if (logger.isLoggable(Level.FINER))
        logger.finer(">< ServletContextAgentIntercept#addFilter(" + AgentRuleUtil.getSimpleNameId(thiz) + "): !(thiz instanceof ServletContext)");

      return false;
    }

    final ServletContext context = (ServletContext)thiz;
    try {
      final FilterRegistration.Dynamic registration = (FilterRegistration.Dynamic)getAddFilterMethod(context);
      if (registration == null)
        return false;

      registration.setAsyncSupported(true);
      registration.addMappingForUrlPatterns(EnumSet.allOf(DispatcherType.class), true, patterns);
      return true;
    }
    catch (final Exception e) {
      logger.log(Level.WARNING, e.getMessage(), e);
      servletContextToFilter.remove(context);
      return false;
    }
    finally {
      if (logger.isLoggable(Level.FINER))
        logger.finer("<< ServletContextAgentIntercept#addFilter(" + AgentRuleUtil.getSimpleNameId(thiz) + ")");
    }
  }
}