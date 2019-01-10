package io.opentracing.contrib.specialagent.webservletfilter;

import java.util.EnumSet;

import org.apache.catalina.core.ApplicationContext;

import io.opentracing.contrib.web.servlet.filter.TracingFilter;
import io.opentracing.util.GlobalTracer;

public class TomcatAgentIntercept {
  public static void exit(final Object thiz) {
    final ApplicationContext context = (ApplicationContext)thiz;
    final TracingFilter filter = new TracingFilter(GlobalTracer.get());
    final String[] patterns = {"/*"};
    context.addFilter("tracingFilter", filter).addMappingForUrlPatterns(EnumSet.allOf(javax.servlet.DispatcherType.class), true, patterns);
  }
}