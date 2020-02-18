package io.opentracing.contrib.specialagent.tracer;

import io.opentracing.contrib.specialagent.Function;

abstract class SpanCustomizer {
  final SpanRules rules;
  private boolean operationNameProcessed = false;

  SpanCustomizer(final SpanRules rules) {
    this.rules = rules;
  }

  abstract void setTag(String key, Object value);
  abstract void addLogField(String key, Object value);
  abstract void setOperationName(String name);

  final void processOperationName(final String operationName) {
    if (operationNameProcessed)
      throw new IllegalStateException();

    operationNameProcessed = true;
    if (!processRules(SpanRuleType.OPERATION_NAME, null, operationName))
      setOperationName(operationName);
  }

  final void processTag(final String key, final Object value) {
    if (!processRules(SpanRuleType.TAG, key, value))
      setTag(key, value);
  }

  final boolean processRules(final SpanRuleType type, final String key, final Object value) {
    for (final SpanRule rule : rules.getSpanRules(key)) {
      if (rule.type != type)
        continue;

      final Function<Object,Object> match = SpanRuleMatcher.match(rule.predicate, value);
      if (match != null) {
        processMatch(rule, match);
        return true;
      }
    }

    return false;
  }

  final void processMatch(final SpanRule rule, final Function<Object,Object> match) {
    if (rule.outputs != null) {
      for (final SpanRuleOutput output : rule.outputs) {
        final Object outputValue = match.apply(output.value);
        final String key = output.key != null ? output.key : rule.key;
        output.type.apply(this, key, outputValue);
      }
    }
  }
}