package io.opentracing.contrib.specialagent.tracer;

import io.opentracing.Span;

public class LogEventCustomizer extends SpanCustomizer {
  private final long timestampMicroseconds;
  private final Span target;
  private final SpanCustomizer base;

  public LogEventCustomizer(final long timestampMicroseconds, final SpanCustomizer customizer, final Span target) {
    this.base = customizer;
    this.timestampMicroseconds = timestampMicroseconds;
    this.target = target;
  }

  void log(final Object value) {
    if (timestampMicroseconds > 0)
      target.log(timestampMicroseconds, value.toString());
    else
      target.log(value.toString());
  }

  @Override
  void addLogField(final String key, final Object value) {
    log(value);
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