package io.opentracing.contrib.specialagent.tracer;

import io.opentracing.Span;

public class LogEventCustomizer implements SpanCustomizer {
  private final long timestampMicroseconds;
  private final Span target;
  private final SpanCustomizer base;

  public LogEventCustomizer(final long timestampMicroseconds, final SpanCustomizer customizer, final Span target) {
    this.base = customizer;
    this.timestampMicroseconds = timestampMicroseconds;
    this.target = target;
  }

  @Override
  public void addLogField(final String key, final Object value) {
    log(value);
  }

  public void log(final Object value) {
    if (timestampMicroseconds > 0) {
      target.log(timestampMicroseconds, value.toString());
    } else {
      target.log(value.toString());
    }
  }

  @Override
  public void setTag(final String key, final Object value) {
    base.setTag(key, value);
  }

  @Override
  public void setOperationName(final String name) {
    base.setOperationName(name);
  }
}