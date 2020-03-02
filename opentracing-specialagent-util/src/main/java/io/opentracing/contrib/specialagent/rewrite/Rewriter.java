package io.opentracing.contrib.specialagent.rewrite;

abstract class Rewriter {
  final RewriteRules rules;

  Rewriter(final RewriteRules rules) {
    this.rules = rules;
  }

  abstract void rewriteTag(String key, Object value);
  abstract void rewriteLog(long timestampMicroseconds, String key, Object value);
  abstract void rewriteOperationName(String name);

  final void onOperationName(final String operationName) {
    if (!onEvent(Event.OperationName.class, 0, null, operationName))
      rewriteOperationName(operationName);
  }

  final void onStart() {
    // Cannot set the span start, only process it
    onEvent(Event.Start.class, 0, null, null);
  }

  final void onLog(final long timestampMicroseconds, final String key, final Object value) {
    if (!onEvent(Event.Log.class, timestampMicroseconds, key, value))
      rewriteLog(timestampMicroseconds, key, value);
  }

  final void onTag(final String key, final Object value) {
    if (!onEvent(Event.Tag.class, 0, key, value))
      rewriteTag(key, value);
  }

  private boolean onEvent(final Class<? extends Event> type, final long timestampMicroseconds, final String key, final Object value) {
    for (final RewriteRule rule : rules.getRules(key)) {
      if (rule.input.getClass() != type)
        continue;

      final Object match = rule.matchValue(value);
      if (match != null) {
        rule.rewrite(this, timestampMicroseconds, match, value);
        return true;
      }
    }

    return false;
  }
}