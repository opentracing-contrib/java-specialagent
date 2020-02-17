package io.opentracing.contrib.specialagent.tracer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.tag.Tag;

public class CustomizableSpanBuilder implements Tracer.SpanBuilder {
  private final SpanCustomizer customizer = new SpanCustomizer() {
    @Override
    public void setTag(final String key, final Object value) {
      if (value == null)
        target.withTag(key, (String)null);
      else if (value instanceof Number)
        target.withTag(key, (Number)value);
      else if (value instanceof Boolean)
        target.withTag(key, (Boolean)value);
      else
        target.withTag(key, value.toString());
    }

    @Override
    public void setOperationName(final String name) {
      operationName = name;
    }

    @Override
    public void addLogField(final String key, final Object value) {
      if (log == null)
        log = new ArrayList<>();

      log.add(Collections.singletonMap(key, value));
    }
  };

  private final Tracer.SpanBuilder target;
  private final SpanRules rules;
  private List<Map<String,Object>> log;
  private String operationName;

  public CustomizableSpanBuilder(final Tracer.SpanBuilder target, final SpanRules rules, final Map<String,Object> tags, final List<Map<String,Object>> log) {
    this.target = target;
    this.rules = rules;
    this.log = log;
    if (tags != null)
      for (Map.Entry<String,Object> entry : tags.entrySet())
        customizer.setTag(entry.getKey(), entry.getValue());
  }

  @Override
  public Tracer.SpanBuilder asChildOf(final SpanContext parent) {
    target.asChildOf(parent);
    return this;
  }

  @Override
  public Tracer.SpanBuilder asChildOf(final Span parent) {
    target.asChildOf(parent);
    return this;
  }

  @Override
  public Tracer.SpanBuilder addReference(final String referenceType, final SpanContext referencedContext) {
    target.addReference(referenceType, referencedContext);
    return this;
  }

  @Override
  public Tracer.SpanBuilder ignoreActiveSpan() {
    target.ignoreActiveSpan();
    return this;
  }

  @Override
  public Tracer.SpanBuilder withTag(final String key, final String value) {
    rules.setTag(key, value, customizer);
    return this;
  }

  @Override
  public Tracer.SpanBuilder withTag(final String key, final boolean value) {
    rules.setTag(key, value, customizer);
    return this;
  }

  @Override
  public Tracer.SpanBuilder withTag(final String key, final Number value) {
    rules.setTag(key, value, customizer);
    return this;
  }

  @Override
  public <T> Tracer.SpanBuilder withTag(final Tag<T> tag, final T value) {
    rules.setTag(tag.getKey(), value, customizer);
    return this;
  }

  @Override
  public Tracer.SpanBuilder withStartTimestamp(final long microseconds) {
    target.withStartTimestamp(microseconds);
    return this;
  }

  @Override
  @Deprecated
  public Span startManual() {
    return target.startManual();
  }

  @Override
  public Span start() {
    final Span span = target.start();
    if (log != null)
      for (final Map<String,Object> fields : log)
        span.log(fields);

    if (operationName != null)
      span.setOperationName(operationName);

    return new CustomizableSpan(span, rules);
  }

  @Override
  @Deprecated
  public Scope startActive(final boolean finishSpanOnClose) {
    return target.startActive(finishSpanOnClose);
  }
}