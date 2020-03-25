package io.opentracing.contrib.specialagent.test.cxf.interceptors;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.PhaseInterceptor;
import org.apache.cxf.tracing.AbstractTracingProvider;
import org.apache.cxf.tracing.opentracing.TraceScope;
import io.opentracing.Span;

public abstract class AbstractSpanTagInterceptor extends AbstractTracingProvider implements PhaseInterceptor<Message> {

  public static final String SPAN_TAG_KEY = "customized_tag";
  public static final String SPAN_TAG_VALUE = "we tag what we want";

  private String phase;

  public AbstractSpanTagInterceptor(final String phase) {
    this.phase = phase;
  }

  @Override
  public void handleMessage(Message message) throws Fault {
    @SuppressWarnings("unchecked")
    final TraceScopeHolder<TraceScope> holder =
        (TraceScopeHolder<TraceScope>) message.getExchange().get(getTraceSpanKey());
    if (holder != null && holder.getScope() != null) {
      TraceScope traceScope = holder.getScope();
      Span span = traceScope.getSpan();
      span.setTag(SPAN_TAG_KEY, SPAN_TAG_VALUE);
    }
  }

  protected abstract String getTraceSpanKey();

  @Override
  public void handleFault(Message message) {}

  @Override
  public Set<String> getAfter() {
    return new HashSet<String>();
  }

  @Override
  public Set<String> getBefore() {
    return new HashSet<String>();
  }

  @Override
  public String getId() {
    return getClass().getName();
  }

  @Override
  public String getPhase() {
    return phase;
  }

  @Override
  public Collection<PhaseInterceptor<? extends Message>> getAdditionalInterceptors() {
    return null;
  }
}
