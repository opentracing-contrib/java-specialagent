package io.opentracing.contrib.specialagent.tracer;

import java.util.LinkedHashMap;
import java.util.Map;

import io.opentracing.Span;

public class LogFieldCustomizer extends SpanCustomizer {
  private final long timestampMicroseconds;
  private final Span target;
  private final SpanCustomizer base;
  private Map<String,Object> fields;

  public LogFieldCustomizer(final long timestampMicroseconds, final SpanCustomizer customizer, final Span target) {
    this.base = customizer;
    this.timestampMicroseconds = timestampMicroseconds;
    this.target = target;
  }

  void log() {
    if (fields != null)
      log(fields);
  }

  void log(final Map<String,?> fields) {
    if (timestampMicroseconds > 0)
      target.log(timestampMicroseconds, fields);
    else
      target.log(fields);
  }

  @Override
  void addLogField(final String key, final Object value) {
    if (fields == null)
      fields = new LinkedHashMap<>();

    fields.put(key, value);
  }

  @Override
  void setTag(final String key, final Object value) {
    base.setTag(key, value);
  }

  @Override
  void setOperationName(final String name) {
    base.setOperationName(name);
  }
}