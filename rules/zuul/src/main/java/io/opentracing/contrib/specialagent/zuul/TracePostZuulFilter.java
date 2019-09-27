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

package io.opentracing.contrib.specialagent.zuul;

import com.netflix.zuul.ZuulFilter;
import com.netflix.zuul.context.RequestContext;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.tag.Tags;
import java.util.HashMap;
import java.util.Map;

public class TracePostZuulFilter extends ZuulFilter {
  private static final String ROUTE_HOST_TAG = "route.host";
  static final String TYPE = "post";

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
    final Object spanObject = context.get(TracePreZuulFilter.CONTEXT_SPAN_KEY);
    if (spanObject instanceof Span) {
      final Span span = (Span) spanObject;
      span.setTag(Tags.HTTP_STATUS.getKey(), context.getResponseStatusCode());
      if (context.getThrowable() != null) {
        onError(context.getThrowable(), span);
      }
      else {
        final Object error = context.get("error.exception");
        if (error instanceof Exception)
          onError((Exception)error, span);
      }

      if (context.getRouteHost() != null)
        span.setTag(ROUTE_HOST_TAG, context.getRouteHost().toString());

      final Object scopeObject = context.get(TracePreZuulFilter.CONTEXT_SCOPE_KEY);
      if (scopeObject instanceof Scope)
        ((Scope)scopeObject).close();

      span.finish();
    }

    return null;
  }

  private static void onError(final Throwable t, final Span span) {
    Tags.ERROR.set(span, Boolean.TRUE);
    if (t != null)
      span.log(errorLogs(t));
  }

  private static Map<String,Object> errorLogs(final Throwable t) {
    final Map<String,Object> errorLogs = new HashMap<>(2);
    errorLogs.put("event", Tags.ERROR.getKey());
    errorLogs.put("error.object", t);
    return errorLogs;
  }
}