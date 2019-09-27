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

package io.opentracing.contrib.specialagent.zuul;

import com.netflix.zuul.ZuulFilter;
import com.netflix.zuul.context.RequestContext;

import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMapAdapter;
import io.opentracing.tag.Tags;

class TracePreZuulFilter extends ZuulFilter {
  private static final String COMPONENT_NAME = "zuul";
  static final String TYPE = "pre";
  static final String CONTEXT_SPAN_KEY = TracePreZuulFilter.class.getName();
  static final String CONTEXT_SCOPE_KEY = TracePreZuulFilter.class.getName() + "-scope";

  private final Tracer tracer;

  TracePreZuulFilter(final Tracer tracer) {
    this.tracer = tracer;
  }

  @Override
  public String filterType() {
    return TYPE;
  }

  @Override
  public int filterOrder() {
    return 0;
  }

  @Override
  public boolean shouldFilter() {
    return true;
  }

  @Override
  public Object run() {
    final RequestContext context = RequestContext.getCurrentContext();

    // span is a child of one created in servlet-filter
    final Span span = tracer.buildSpan(context.getRequest().getMethod()).withTag(Tags.COMPONENT.getKey(), COMPONENT_NAME).start();
    tracer.inject(span.context(), Format.Builtin.HTTP_HEADERS, new TextMapAdapter(context.getZuulRequestHeaders()));
    context.set(CONTEXT_SPAN_KEY, span);

    final Scope scope = tracer.activateSpan(span);
    context.set(CONTEXT_SCOPE_KEY, scope);

    return null;
  }
}