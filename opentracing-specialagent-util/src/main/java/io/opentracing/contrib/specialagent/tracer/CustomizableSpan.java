package io.opentracing.contrib.specialagent.tracer;

import java.util.Collections;
import java.util.Map;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.tag.Tag;

public class CustomizableSpan implements Span {
  private final Span target;
  private final SpanRules rules;
  private SpanCustomizer customizer = new SpanCustomizer() {
    @Override
    public void addLogField(final String key, final Object value) {
      target.log(Collections.singletonMap(key, value));
    }

    @Override
    public void setTag(final String key, final Object value) {
      if (value == null)
        target.setTag(key, (String)null);
      else if (value instanceof Number)
        target.setTag(key, (Number)value);
      else if (value instanceof Boolean)
        target.setTag(key, (Boolean)value);
      else
        target.setTag(key, value.toString());
    }

    @Override
    public void setOperationName(final String name) {
      target.setOperationName(name);
    }
  };

  public CustomizableSpan(final Span target, final SpanRules rules) {
    this.target = target;
    this.rules = rules;
  }

  @Override
  public SpanContext context() {
    return target.context();
  }

  @Override
  public Span setTag(final String key, final String value) {
    rules.setTag(key, value, customizer);
    return this;
  }

  @Override
  public Span setTag(final String key, final boolean value) {
    rules.setTag(key, value, customizer);
    return this;
  }

  @Override
  public Span setTag(final String key, final Number value) {
    rules.setTag(key, value, customizer);
    return this;
  }

  @Override
  public <T> Span setTag(final Tag<T> tag, final T value) {
    rules.setTag(tag.getKey(), value, customizer);
    return this;
  }

  @Override
  public Span log(Map<String,?> fields) {
    rules.log(fields, new LogFieldCustomizer(0, customizer, target));
    return this;
  }

  @Override
  public Span log(final long timestampMicroseconds, final Map<String,?> fields) {
    rules.log(fields, new LogFieldCustomizer(timestampMicroseconds, customizer, target));
    return this;
  }

  @Override
  public Span log(final String event) {
    rules.log(event, new LogEventCustomizer(0, customizer, target));
    return this;
  }

  @Override
  public Span log(final long timestampMicroseconds, final String event) {
    rules.log(event, new LogEventCustomizer(timestampMicroseconds, customizer, target));
    return this;
  }

  @Override
  public Span setBaggageItem(final String key, final String value) {
    target.setBaggageItem(key, value);
    return this;
  }

  @Override
  public String getBaggageItem(final String key) {
    return target.getBaggageItem(key);
  }

  @Override
  public Span setOperationName(final String operationName) {
    rules.processOperationName(operationName, customizer);
    return this;
  }

  @Override
  public void finish() {
    target.finish();
  }

  @Override
  public void finish(final long finishMicros) {
    target.finish(finishMicros);
  }
}