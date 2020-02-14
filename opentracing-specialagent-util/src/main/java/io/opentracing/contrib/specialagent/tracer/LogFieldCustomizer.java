package io.opentracing.contrib.specialagent.tracer;

import io.opentracing.Span;

import java.util.LinkedHashMap;
import java.util.Map;

public class LogFieldCustomizer implements SpanCustomizer {
  private final long timestampMicroseconds;
  private final Span target;
  private final SpanCustomizer base;
  private Map<String, Object> fields;

  public LogFieldCustomizer(long timestampMicroseconds, SpanCustomizer customizer, Span target) {
    this.base = customizer;
    this.timestampMicroseconds = timestampMicroseconds;
    this.target = target;
  }

  @Override
  public void addLogField(String key, Object value) {
    if (fields == null) {
      fields = new LinkedHashMap<>();
    }
    fields.put(key, value);
  }

  public void finish() {
    if (fields != null) {
      log(fields);
    }
  }

  public void log(Map<String, ?> fields) {
    if (timestampMicroseconds > 0) {
      target.log(timestampMicroseconds, fields);
    } else {
      target.log(fields);
    }
  }

  @Override
  public void setTag(String key, Object value) {
    base.setTag(key, value);
  }

  @Override
  public void setOperationName(String name) {
    base.setOperationName(name);
  }
}
