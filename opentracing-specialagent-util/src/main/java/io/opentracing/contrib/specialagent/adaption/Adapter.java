package io.opentracing.contrib.specialagent.adaption;

import io.opentracing.contrib.specialagent.Function;

abstract class Adapter {
  final AdaptionRules rules;

  Adapter(final AdaptionRules rules) {
    this.rules = rules;
  }

  abstract void setTag(String key, Object value);
  abstract void addLogField(String key, Object value);
  abstract void setOperationName(String name);

  final boolean processRules(final AdaptionRuleType type, final String key, final Object value) {
    for (final AdaptionRule<?> rule : rules.getSpanRules(key)) {
      if (rule.type != type)
        continue;

      final Function<Object,Object> match = rule.match(value);
      if (match != null) {
        processMatch(rule, match);
        return true;
      }
    }

    return false;
  }

  final void processMatch(final AdaptionRule<?> rule, final Function<Object,Object> match) {
    if (rule.outputs != null) {
      for (final AdaptedOutput output : rule.outputs) {
        final Object outputValue = match.apply(output.value);
        final String key = output.key != null ? output.key : rule.key;
        output.type.apply(this, key, outputValue);
      }
    }
  }}