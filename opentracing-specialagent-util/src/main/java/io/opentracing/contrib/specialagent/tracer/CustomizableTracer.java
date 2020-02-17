package io.opentracing.contrib.specialagent.tracer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.opentracing.Scope;
import io.opentracing.ScopeManager;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;

public class CustomizableTracer extends SpanCustomizer implements Tracer {
  private final Tracer target;
  private final SpanRules rules;

  public CustomizableTracer(final Tracer target, final SpanRules rules) {
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
  public Scope activateSpan(final Span span) {
    return target.activateSpan(span);
  }

  @Override
  public SpanBuilder buildSpan(final String operationName) {
    if (log != null)
      log.clear();

    if (tags != null)
      tags.clear();

    rules.processOperationName(operationName, this);
    return new CustomizableSpanBuilder(target.buildSpan(operationName), rules, tags, log);
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

  private List<Map<String,Object>> log;
  private Map<String,Object> tags;

  @Override
  void setTag(final String key, final Object value) {
    if (tags == null)
      tags = new LinkedHashMap<>();

    tags.put(key, value);
  }

  @Override
  void addLogField(final String key, final Object value) {
    if (log == null)
      log = new ArrayList<>();

    log.add(Collections.singletonMap(key, value));
  }

  @Override
  void setOperationName(final String name) {
  }
}