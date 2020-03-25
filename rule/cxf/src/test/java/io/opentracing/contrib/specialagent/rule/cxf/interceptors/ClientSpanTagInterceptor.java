package io.opentracing.contrib.specialagent.rule.cxf.interceptors;

import java.util.Set;
import org.apache.cxf.phase.Phase;
import org.apache.cxf.tracing.opentracing.OpenTracingClientStopInterceptor;

public class ClientSpanTagInterceptor extends AbstractSpanTagInterceptor {
  
  private static final String TRACE_SPAN = "org.apache.cxf.tracing.client.opentracing.span";

  public ClientSpanTagInterceptor() {
    super(Phase.RECEIVE);
  }
  
  public Set<String> getBefore() {
    final Set<String> before = super.getBefore();
    before.add(OpenTracingClientStopInterceptor.class.getName());
    return before;
}

  @Override
  protected String getTraceSpanKey() {
    return TRACE_SPAN;
  }
}
