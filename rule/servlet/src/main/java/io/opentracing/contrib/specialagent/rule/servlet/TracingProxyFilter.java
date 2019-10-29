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

package io.opentracing.contrib.specialagent.rule.servlet;

import java.util.Enumeration;

import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;

import io.opentracing.Tracer;
import io.opentracing.contrib.web.servlet.filter.TracingFilter;

public class TracingProxyFilter extends TracingFilter implements FilterConfig {
  private final ServletContext context;

  public TracingProxyFilter(final Tracer tracer, final ServletContext context) throws ServletException {
    super(tracer);
    this.context = context;
    init(this);
  }

  @Override
  public String getFilterName() {
    return null;
  }

  @Override
  public ServletContext getServletContext() {
    return context;
  }

  @Override
  public String getInitParameter(final String name) {
    return null;
  }

  @Override
  public Enumeration<String> getInitParameterNames() {
    return null;
  }
}