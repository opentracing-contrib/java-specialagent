package io.opentracing.contrib.specialagent.tracer;

import java.util.LinkedHashMap;
import java.util.Map;

import io.opentracing.Span;

public class LogFieldCustomizer extends SpanCustomizer {
  private final long timestampMicroseconds;
  private final SpanCustomizer customizer;
  private final Span target;

  private Map<String,Object> fields;

  public LogFieldCustomizer(final long timestampMicroseconds, final SpanCustomizer customizer, final Span target, final SpanRules rules) {
    super(rules);
    this.timestampMicroseconds = timestampMicroseconds;
    this.customizer = customizer;
    this.target = target;
  }

  void log(final Map<String,?> fields) {
    if (timestampMicroseconds > 0)
      target.log(timestampMicroseconds, fields);
    else
      target.log(fields);
  }

  void log() {
    if (fields != null)
      log(fields);
  }

  @Override
  void addLogField(final String key, final Object value) {
    if (fields == null)
      fields = new LinkedHashMap<>();

    fields.put(key, value);
  }

  @Override
  void setTag(final String key, final Object value) {
    customizer.setTag(key, value);
  }

  @Override
  void setOperationName(final String name) {
    customizer.setOperationName(name);
  }
}