package io.opentracing.contrib.specialagent.tracer;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.tag.Tag;

import java.util.Collections;
import java.util.Map;

public class CustomizableSpan implements Span {
  private final SpanCustomizer customizer;

  private final Span target;
  private final SpanRules rules;

  public CustomizableSpan(final Span target, final SpanRules rules) {
    this.customizer = new SpanCustomizer(rules) {
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

    this.target = target;
    this.rules = rules;
  }

  @Override
  public SpanContext context() {
    return target.context();
  }

  @Override
  public Span setTag(final String key, final String value) {
    customizer.processTag(key, value);
    return this;
  }

  @Override
  public Span setTag(final String key, final boolean value) {
    customizer.processTag(key, value);
    return this;
  }

  @Override
  public Span setTag(final String key, final Number value) {
    customizer.processTag(key, value);
    return this;
  }

  @Override
  public <T>Span setTag(final Tag<T> tag, final T value) {
    customizer.processTag(tag.getKey(), value);
    return this;
  }

  @Override
  public Span log(final Map<String,?> fields) {
    logFieldCustomizer().processLog(0, fields);
    return this;
  }

  @Override
  public Span log(final long timestampMicroseconds, final Map<String,?> fields) {
    logFieldCustomizer().processLog(timestampMicroseconds, fields);
    return this;
  }

  LogFieldCustomizer logFieldCustomizer() {
    return new LogFieldCustomizer(rules, customizer, target);
  }

  @Override
  public Span log(final String event) {
    new LogEventCustomizer(rules, 0, customizer, target).processLog(event);
    return this;
  }

  @Override
  public Span log(final long timestampMicroseconds, final String event) {
    new LogEventCustomizer(rules, timestampMicroseconds, customizer, target).processLog(event);
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
    customizer.processOperationName(operationName);
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
