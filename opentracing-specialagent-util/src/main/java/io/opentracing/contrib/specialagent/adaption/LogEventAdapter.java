package io.opentracing.contrib.specialagent.adaption;

import io.opentracing.Span;

final class LogEventAdapter extends Adapter {
  private final Adaptive source;
  private final Span target;

  LogEventAdapter(final AdaptionRules rules, final Adaptive source, final Span target) {
    super(rules);
    this.source = source;
    this.target = target;
  }

  void processLog(final long timestampMicroseconds, final String event) {
    if (!processRules(AdaptionType.LOG, timestampMicroseconds, null, event))
      adaptLog(timestampMicroseconds, null, event);
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
