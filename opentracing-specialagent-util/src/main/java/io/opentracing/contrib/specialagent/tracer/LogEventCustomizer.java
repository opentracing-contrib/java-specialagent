package io.opentracing.contrib.specialagent.tracer;

import io.opentracing.Span;

public class LogEventCustomizer implements SpanCustomizer {
  private final long timestampMicroseconds;
  private final Span target;
  private final SpanCustomizer base;

  public LogEventCustomizer(long timestampMicroseconds, SpanCustomizer customizer, Span target) {
    this.base = customizer;
    this.timestampMicroseconds = timestampMicroseconds;
    this.target = target;
  }

  @Override
  public void addLogField(String key, Object value) {
    log(value);
  }

  public void log(Object value) {
    if (timestampMicroseconds > 0) {
      target.log(timestampMicroseconds, value.toString());
    } else {
      target.log(value.toString());
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
