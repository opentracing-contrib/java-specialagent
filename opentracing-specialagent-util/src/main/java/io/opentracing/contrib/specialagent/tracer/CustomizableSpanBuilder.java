package io.opentracing.contrib.specialagent.tracer;

import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.tag.Tag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CustomizableSpanBuilder extends SpanCustomizer implements Tracer.SpanBuilder {
  private final SpanCustomizer customizer;
  private final Tracer.SpanBuilder target;

  public CustomizableSpanBuilder(final String operationName, final Tracer target, final SpanRules rules) {
    super(rules);
    this.customizer = new SpanCustomizer(rules) {
      @Override
      public void setTag(final String key, final Object value) {
        if (value == null)
          CustomizableSpanBuilder.this.target.withTag(key, (String) null);
        else if (value instanceof Number)
          CustomizableSpanBuilder.this.target.withTag(key, (Number) value);
        else if (value instanceof Boolean)
          CustomizableSpanBuilder.this.target.withTag(key, (Boolean) value);
        else
          CustomizableSpanBuilder.this.target.withTag(key, value.toString());
      }

      @Override
      public void setOperationName(final String name) {
        CustomizableSpanBuilder.this.operationName = name;
      }

      @Override
      public void addLogField(final String key, final Object value) {
        if (log == null)
          log = new ArrayList<>();

        log.add(Collections.singletonMap(key, value));
      }
    };

    processOperationName(operationName);
    this.target = target.buildSpan(operationName);
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
    customizer.processTag(key, value);
    return this;
  }

  @Override
  public Tracer.SpanBuilder withTag(final String key, final boolean value) {
    customizer.processTag(key, value);
    return this;
  }

  @Override
  public Tracer.SpanBuilder withTag(final String key, final Number value) {
    customizer.processTag(key, value);
    return this;
  }

  @Override
  public <T> Tracer.SpanBuilder withTag(final Tag<T> tag, final T value) {
    customizer.processTag(tag.getKey(), value);
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

    return getCustomizableSpan(span);
  }

  protected CustomizableSpan getCustomizableSpan(final Span span) {
    return new CustomizableSpan(span, rules);
  }

  @Override
  @Deprecated
  public Scope startActive(final boolean finishSpanOnClose) {
    return target.startActive(finishSpanOnClose);
  }

  private String operationName;
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
    this.operationName = name;
  }

  List<Map<String, Object>> getLog() {
    return log;
  }

  Map<String, Object> getTags() {
    return tags;
  }
}
