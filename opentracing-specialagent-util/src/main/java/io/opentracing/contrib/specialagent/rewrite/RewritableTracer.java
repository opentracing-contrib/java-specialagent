package io.opentracing.contrib.specialagent.rewrite;

import io.opentracing.Scope;
import io.opentracing.ScopeManager;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;

public class RewritableTracer implements Tracer {
  final Tracer target;
  final RewriteRules rules;

  RewritableTracer(final Tracer target, final RewriteRules rules) {
    this.target = target;
    this.rules = rules;
  }

  @Override
  public ScopeManager scopeManager() {
    return target.scopeManager();
  }

  @Override
  public Span activeSpan() {
    return new RewritableSpan(target.activeSpan(), rules);
  }

  @Override
  public Scope activateSpan(final Span span) {
    return target.activateSpan(span);
  }

  @Override
  public SpanBuilder buildSpan(final String operationName) {
    return new RewritableSpanBuilder(operationName, target, rules);
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