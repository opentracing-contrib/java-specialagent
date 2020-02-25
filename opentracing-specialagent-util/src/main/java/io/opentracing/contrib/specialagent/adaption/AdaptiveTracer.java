package io.opentracing.contrib.specialagent.adaption;

import io.opentracing.Scope;
import io.opentracing.ScopeManager;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;

public class AdaptiveTracer implements Tracer {
  final Tracer target;
  final AdaptionRules rules;
  private final String serviceName;

  AdaptiveTracer(final Tracer target, final AdaptionRules rules) {
    this.target = target;
    this.rules = rules;
    this.serviceName = TracerIntrospector.getServiceName(target);
  }

  @Override
  public ScopeManager scopeManager() {
    return target.scopeManager();
  }

  @Override
  public Span activeSpan() {
    return new AdaptiveSpan(target.activeSpan(), rules);
  }

  @Override
  public Scope activateSpan(final Span span) {
    return target.activateSpan(span);
  }

  @Override
  public SpanBuilder buildSpan(final String operationName) {
    return new AdaptiveSpanBuilder(operationName, target, rules, serviceName);
  }

  @Override
  public <C>void inject(final SpanContext spanContext, final Format<C> format, final C carrier) {
    target.inject(spanContext, format, carrier);
  }

  @Override
  public <C>SpanContext extract(final Format<C> format, final C carrier) {
    return target.extract(format, carrier);
  }

  @Override
  public void close() {
    target.close();
  }
}
