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

import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;

import io.opentracing.contrib.web.servlet.filter.TracingFilter;
import io.opentracing.util.GlobalTracer;

public abstract class ServletFilterAgentIntercept {
  public static final Map<ServletRequest,Boolean> servletRequestToState = new WeakHashMap<>();
  public static final Map<ServletContext,TracingFilter> servletContextToFilter = new WeakHashMap<>();

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
}