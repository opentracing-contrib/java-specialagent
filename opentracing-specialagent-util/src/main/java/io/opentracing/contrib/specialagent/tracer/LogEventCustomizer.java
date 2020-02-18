package io.opentracing.contrib.specialagent.tracer;

import io.opentracing.Span;

public class LogEventCustomizer extends SpanCustomizer {
  private final long timestampMicroseconds;
  private final Span target;
  private final SpanCustomizer source;

  public LogEventCustomizer(final SpanRules rules, final long timestampMicroseconds, final SpanCustomizer source, final Span target) {
    super(rules);
    this.timestampMicroseconds = timestampMicroseconds;
    this.source = source;
    this.target = target;
  }

  final void processLog(final String event) {
    if (!processRules(SpanRuleType.LOG, null, event))
      log(event);
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
    source.setTag(key, value);
  }

  @Override
  void setOperationName(final String name) {
    source.setOperationName(name);
  }
}