package io.opentracing.contrib.specialagent.adaption;

import io.opentracing.Span;

final class LogEventAdapter extends Adapter {
  private final Adapter source;
  private final Span target;

  LogEventAdapter(final AdaptionRules rules, final Adapter source, final Span target) {
    super(rules);
    this.source = source;
    this.target = target;
  }

  @Override
  void adaptLog(final long timestampMicroseconds, final String key, final Object value) {
    if (timestampMicroseconds > 0)
      target.log(timestampMicroseconds, value.toString());
    else
      target.log(value.toString());
  }

  @Override
  void adaptTag(final String key, final Object value) {
    source.adaptTag(key, value);
  }

  @Override
  void adaptOperationName(final String name) {
    source.adaptOperationName(name);
  }
}