package io.opentracing.contrib.specialagent.camel;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.opentracing.OpenTracingTracer;

public class ContextAgentIntercept {
  public static final Map<DefaultCamelContext,Object> state = Collections.synchronizedMap(new WeakHashMap<DefaultCamelContext,Object>());

  public static void enter(final Object thiz) {
    final DefaultCamelContext context = (DefaultCamelContext)thiz;
    if (state.containsKey(context))
      return;

    final OpenTracingTracer tracer = new OpenTracingTracer();
    tracer.init(context);
    state.put(context, null);
  }
}