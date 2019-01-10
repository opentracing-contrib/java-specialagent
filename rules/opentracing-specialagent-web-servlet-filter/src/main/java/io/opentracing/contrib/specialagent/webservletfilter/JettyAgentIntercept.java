package io.opentracing.contrib.specialagent.webservletfilter;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.WeakHashMap;

import org.eclipse.jetty.server.handler.ContextHandler.Context;
import org.eclipse.jetty.servlet.ServletContextHandler;

import io.opentracing.contrib.web.servlet.filter.TracingFilter;
import io.opentracing.util.GlobalTracer;

public class JettyAgentIntercept {
  public static final Map<Context,Object> state = Collections.synchronizedMap(new WeakHashMap<Context,Object>());

  public static void exit(final Object thiz) {
    final ServletContextHandler handler = (ServletContextHandler)thiz;
    final Context context = handler.getServletContext();
    if (state.containsKey(context))
      return;

    final TracingFilter filter = new TracingFilter(GlobalTracer.get());
    final String[] patterns = {"/*"};
    context.addFilter("tracingFilter", filter).addMappingForUrlPatterns(EnumSet.allOf(javax.servlet.DispatcherType.class), true, patterns);
    state.put(context, null);
  }
}