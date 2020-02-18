package io.opentracing.contrib.specialagent.tracer;

import io.opentracing.Span;

public class LogEventCustomizer extends SpanCustomizer {
  private final long timestampMicroseconds;
  private final Span target;
  private final SpanCustomizer customized;

  public LogEventCustomizer(final long timestampMicroseconds, final SpanCustomizer customizer, final Span target, final SpanRules rules) {
    super(rules);
    this.timestampMicroseconds = timestampMicroseconds;
    this.customized = customizer;
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
    customized.setTag(key, value);
  }

  @Override
  void setOperationName(final String name) {
    customized.setOperationName(name);
  }
}