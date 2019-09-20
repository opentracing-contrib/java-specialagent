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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.ServletContext;

import org.apache.catalina.Container;
import org.apache.catalina.ServerFactory;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.core.StandardEngine;
import org.apache.catalina.deploy.FilterDef;
import org.apache.catalina.deploy.FilterMap;

import io.opentracing.contrib.specialagent.AgentRuleUtil;
import io.opentracing.contrib.specialagent.Level;
import io.opentracing.contrib.web.servlet.filter.TracingFilter;

public class TomcatAgentIntercept extends ContextAgentIntercept {
  public static final Map<ServletContext,Boolean> servletContextToState = new ConcurrentHashMap<>();
  public static volatile boolean compatible = true;

  public static boolean addFilter(final ServletContext context) {
    if (!compatible)
      return false;

    if (servletContextToFilter.containsKey(context) || servletContextToState.containsKey(context))
      return true;

    try {
      final StandardEngine engine = (StandardEngine)ServerFactory.getServer().findService("Catalina").getContainer();
      final Container container = engine.findChild(engine.getDefaultHost());
      final StandardContext standardContext = (StandardContext)container.findChild(context.getContextPath());

      final FilterDef filterDef = new FilterDef();
      filterDef.setFilterName(TracingFilter.class.getSimpleName());
      filterDef.setFilterClass(TracingFilter.class.getName());
      standardContext.addFilterDef(filterDef);

      final FilterMap filterMap = new FilterMap();
      filterMap.setFilterName(TracingFilter.class.getSimpleName());
      filterMap.addURLPattern(patterns[0]);
      standardContext.addFilterMap(filterMap);

      servletContextToState.put(context, Boolean.TRUE);
      if (logger.isLoggable(Level.FINER))
        logger.finer("<< TomcatAgentIntercept#addFilter(" + AgentRuleUtil.getSimpleNameId(context) + ")");

      return true;
    }
    catch (final Exception e) {
      if (logger.isLoggable(Level.FINER))
        logger.log(Level.FINER, "<><><><> TomcatAgentIntercept#addFilter(" + AgentRuleUtil.getSimpleNameId(context) + ")", e);

      return compatible = false;
    }
  }
}