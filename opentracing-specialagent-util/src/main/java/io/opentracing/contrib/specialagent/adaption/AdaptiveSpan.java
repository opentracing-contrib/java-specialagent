package io.opentracing.contrib.specialagent.adaption;

import java.util.Collections;
import java.util.Map;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.tag.Tag;

public class AdaptiveSpan implements Span {
  private final Adaptive adaptive;

  private final Span target;
  private final AdaptionRules rules;

  AdaptiveSpan(final Span target, final AdaptionRules rules) {
    this.adaptive = new Adaptive(rules) {
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
    adaptive.processTag(key, value);
    return this;
  }

  @Override
  public Span setTag(final String key, final boolean value) {
    adaptive.processTag(key, value);
    return this;
  }

  @Override
  public Span setTag(final String key, final Number value) {
    adaptive.processTag(key, value);
    return this;
  }

  @Override
  public <T> Span setTag(final Tag<T> tag, final T value) {
    adaptive.processTag(tag.getKey(), value);
    return this;
  }

  @Override
  public Span log(final Map<String,?> fields) {
    newLogFieldCustomizer().processLog(0, fields);
    return this;
  }

  @Override
  public Span log(final long timestampMicroseconds, final Map<String,?> fields) {
    newLogFieldCustomizer().processLog(timestampMicroseconds, fields);
    return this;
  }

  LogFieldAdapter newLogFieldCustomizer() {
    return new LogFieldAdapter(rules, adaptive, target);
  }

  @Override
  public Span log(final String event) {
    new LogEventAdapter(rules, 0, adaptive, target).processLog(event);
    return this;
  }

  @Override
  public Span log(final long timestampMicroseconds, final String event) {
    new LogEventAdapter(rules, timestampMicroseconds, adaptive, target).processLog(event);
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
    adaptive.processOperationName(operationName);
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