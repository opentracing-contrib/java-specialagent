package io.opentracing.contrib.specialagent.tracer;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.tag.Tag;

import java.util.Collections;
import java.util.Map;

public class CustomizableSpan implements Span {
  private final Span target;
  private final SpanRules rules;
  private SpanCustomizer customizer = new SpanCustomizer() {

    @Override
    public void addLogField(String key, Object value) {
      target.log(Collections.singletonMap(key, value));
    }

    @Override
    public void setTag(String key, Object value) {
      if (value == null) {
        target.setTag(key, (String) null);
      } else if (value instanceof Number) {
        target.setTag(key, (Number) value);
      } else if (value instanceof Boolean) {
        target.setTag(key, (Boolean) value);
      } else {
        target.setTag(key, value.toString());
      }
    }

    @Override
    public void setOperationName(String name) {
      target.setOperationName(name);
    }
  };

  public CustomizableSpan(Span target, SpanRules rules) {
    this.target = target;
    this.rules = rules;
  }

  @Override
  public SpanContext context() {
    return target.context();
  }

  @Override
  public Span setTag(String key, String value) {
    rules.setTag(key, value, customizer);
    return this;
  }

  @Override
  public Span setTag(String key, boolean value) {
    rules.setTag(key, value, customizer);
    return this;
  }

  @Override
  public Span setTag(String key, Number value) {
    rules.setTag(key, value, customizer);
    return this;
  }

  @Override
  public <T> Span setTag(Tag<T> tag, T value) {
    rules.setTag(tag.getKey(), value, customizer);
    return this;
  }

  @Override
  public Span log(Map<String, ?> fields) {
    rules.log(fields, new LogFieldCustomizer(0,customizer, target));
    return this;
  }

  @Override
  public Span log(long timestampMicroseconds, Map<String, ?> fields) {
    rules.log(fields, new LogFieldCustomizer(timestampMicroseconds,customizer, target));
    return this;
  }

  @Override
  public Span log(String event) {
    rules.log(event, new LogEventCustomizer(0, customizer, target));
    return this;
  }

  @Override
  public Span log(long timestampMicroseconds, String event) {
    rules.log(event, new LogEventCustomizer(timestampMicroseconds, customizer, target));
    return this;
  }

  @Override
  public Span setBaggageItem(String key, String value) {
    target.setBaggageItem(key, value);
    return this;
  }

  @Override
  public String getBaggageItem(String key) {
    return target.getBaggageItem(key);
  }

  @Override
  public Span setOperationName(String operationName) {
    rules.processOperationName(operationName, customizer);
    return this;
  }

  @Override
  public void finish() {
    target.finish();
  }

  @Override
  public void finish(long finishMicros) {
    target.finish(finishMicros);
  }
}
