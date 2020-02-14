package io.opentracing.contrib.specialagent.tracer;

import io.opentracing.Scope;
import io.opentracing.ScopeManager;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;

public class CustomizableTracer implements Tracer {
  private final Tracer target;
  private final SpanRules rules;

  public CustomizableTracer(Tracer target, SpanRules rules) {
    this.target = target;
    this.rules = rules;
  }

  @Override
  public ScopeManager scopeManager() {
    return target.scopeManager();
  }

  @Override
  public Span activeSpan() {
    return new CustomizableSpan(target.activeSpan(), rules);
  }

  @Override
  public Scope activateSpan(Span span) {
    return target.activateSpan(span);
  }

  @Override
  public SpanBuilder buildSpan(String operationName) {
    return new OperationNameCustomizer(operationName).buildSpan(target, rules);
  }

  @Override
  public <C> void inject(SpanContext spanContext, Format<C> format, C carrier) {
    target.inject(spanContext, format, carrier);
  }

  @Override
  public <C> SpanContext extract(Format<C> format, C carrier) {
    return target.extract(format, carrier);
  }

  @Override
  public void close() {
    target.close();
  }
}

