package io.opentracing.contrib.specialagent.adaption;

abstract class Adapter {
  final AdaptionRules rules;

  Adapter(final AdaptionRules rules) {
    this.rules = rules;
  }

  abstract void adaptTag(String key, Object value);
  abstract void adaptLog(long timestampMicroseconds, String key, Object value);
  abstract void adaptOperationName(String name);

  final boolean processRules(final AdaptionType type, final long timestampMicroseconds, final String key, final Object value) {
    for (final AdaptionRule<?,?> rule : rules.getSpanRules(key)) {
      if (rule.type != type)
        continue;

      final Object match = rule.match(value);
      if (match != null) {
        processMatch(rule, timestampMicroseconds, match, value);
        return true;
      }
    }

    return false;
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  final void processMatch(final AdaptionRule rule, final long timestampMicroseconds, final Object match, final Object input) {
    if (rule.outputs != null) {
      for (final AdaptedOutput adaptedOutput : rule.outputs) {
        final String key = adaptedOutput.key != null ? adaptedOutput.key : rule.key;
        final Object output = rule.adapt(match, input, adaptedOutput.value);
        adaptedOutput.type.adapt(this, timestampMicroseconds, key, output);
      }
    }
  }
}