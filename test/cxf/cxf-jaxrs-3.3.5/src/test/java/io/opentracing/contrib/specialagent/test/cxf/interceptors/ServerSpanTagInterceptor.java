package io.opentracing.contrib.specialagent.test.cxf.interceptors;

import java.util.Set;
import org.apache.cxf.phase.Phase;
import org.apache.cxf.tracing.opentracing.OpenTracingStopInterceptor;

public class ServerSpanTagInterceptor extends AbstractSpanTagInterceptor {
  
  private static final String TRACE_SPAN = "org.apache.cxf.tracing.opentracing.span";

  public ServerSpanTagInterceptor() {
    super(Phase.PRE_MARSHAL);
  }
  
  public Set<String> getBefore() {
    final Set<String> before = super.getBefore();
    before.add(OpenTracingStopInterceptor.class.getName());
    return before;
}

  @Override
  protected String getTraceSpanKey() {
    return TRACE_SPAN;
  }
}
