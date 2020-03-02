package io.opentracing.contrib.specialagent.rewrite;

import io.opentracing.Span;

final class LogEventRewriter extends Rewriter {
  private final Rewriter source;
  private final Span target;

  LogEventRewriter(final RewriteRules rules, final Rewriter source, final Span target) {
    super(rules);
    this.source = source;
    this.target = target;
  }

  @Override
  void rewriteLog(final long timestampMicroseconds, final String key, final Object value) {
    if (timestampMicroseconds > 0)
      target.log(timestampMicroseconds, value.toString());
    else
      target.log(value.toString());
  }

  @Override
  void rewriteTag(final String key, final Object value) {
    source.rewriteTag(key, value);
  }

  @Override
  void rewriteOperationName(final String name) {
    source.rewriteOperationName(name);
  }
}