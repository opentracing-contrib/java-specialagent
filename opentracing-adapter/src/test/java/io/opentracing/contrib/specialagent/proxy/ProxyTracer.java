package io.opentracing.contrib.specialagent.proxy;

import io.opentracing.Scope;
import io.opentracing.ScopeManager;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.contrib.specialagent.ProxyTest;
import io.opentracing.propagation.Format;

public class ProxyTracer implements Tracer {
  private final Tracer tracer;

  public ProxyTracer(final Tracer tracer) {
    this.tracer = ProxyTest.proxy(tracer);
  }

  private ProxyScopeManager proxyScopeManager;

  @Override
  public ScopeManager scopeManager() {
    return tracer.scopeManager();
  }

  @Override
  public ProxySpan activeSpan() {
//    final Span activeSpan = tracer.activeSpan();
//    return consumerSpan == null || consumerSpan.span != activeSpan ? consumerSpan = new ConsumerSpan(activeSpan) : consumerSpan;
    return null;
  }

  @Override
  public ProxySpanBuilder buildSpan(final String operationName) {
    return new ProxySpanBuilder(tracer.buildSpan(operationName));
  }

  @Override
  public <C>void inject(final SpanContext spanContext, final Format<C> format, final C carrier) {
  }

  @Override
  public <C>ProxySpanContext extract(final Format<C> format, C carrier) {
    return null;
  }

  @Override
  public Scope activateSpan(Span span) {
    return null;
  }

  @Override
  public void close() {
  }
}