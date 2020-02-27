package io.opentracing.contrib.specialagent.adaption;

abstract class Adapter {
  final AdaptionRules rules;

  Adapter(final AdaptionRules rules) {
    this.rules = rules;
  }

  abstract void adaptTag(String key, Object value);
  abstract void adaptLogField(String key, Object value);
  abstract void adaptOperationName(String name);

  final boolean processRules(final AdaptionRuleType type, final String key, final Object value) {
    for (final AdaptionRule<?,?> rule : rules.getSpanRules(key)) {
      if (rule.type != type)
        continue;

      final Object match = rule.match(value);
      if (match != null) {
        processMatch(rule, match, value);
        return true;
      }
    }

    return false;
  }

  final void processMatch(final AdaptionRule rule, final Object match, final Object input) {
    if (rule.outputs != null) {
      for (final AdaptedOutput adaptedOutput : rule.outputs) {
        final Object output = rule.adapt(match, input, adaptedOutput.value);
        final String key = adaptedOutput.key != null ? adaptedOutput.key : rule.key;
        adaptedOutput.type.apply(this, key, output);
      }
    }
  }
}