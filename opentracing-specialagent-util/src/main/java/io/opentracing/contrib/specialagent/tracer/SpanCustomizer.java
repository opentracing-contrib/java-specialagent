package io.opentracing.contrib.specialagent.tracer;

import java.util.Map;

import io.opentracing.contrib.specialagent.Function;
import io.opentracing.contrib.specialagent.Logger;

abstract class SpanCustomizer {
  private static final Logger logger = Logger.getLogger(SpanCustomizer.class);

  final SpanRules rules;
  private boolean processed = false;

  SpanCustomizer(final SpanRules rules) {
    this.rules = rules;
  }

  abstract void setTag(String key, Object value);
  abstract void addLogField(String key, Object value);
  abstract void setOperationName(String name);

  final void processOperationName(final String operationName) {
    if (!processRules(SpanRuleType.OPERATION_NAME, null, operationName, this))
      setOperationName(operationName);
  }

  void processTag(final String key, final Object value) {
    if (!processRules(SpanRuleType.TAG, key, value, this))
      setTag(key, value);
  }

  void processLog(final String event, final LogEventCustomizer builder) {
    if (!processRules(SpanRuleType.LOG, null, event, builder))
      builder.log(event);
  }

  void log(final Map<String,?> fields, final LogFieldCustomizer builder) {
    for (final Map.Entry<String,?> entry : fields.entrySet()) {
      final String key = entry.getKey();
      final Object value = entry.getValue();
      for (final SpanRule rule : rules.getSpanRules(key)) {
        if (rule.type != SpanRuleType.LOG)
          continue;

        final Function<Object,Object> match = SpanRuleMatcher.match(rule.predicate, value);
        if (match != null) {
          replaceLog(fields, rule, match, builder);
          return;
        }
      }
    }

    // nothing matched
    builder.log(fields);
  }

  private boolean processRules(final SpanRuleType type, final String key, final Object value, final SpanCustomizer customizer) {
    if (processed)
      logger.severe("Called twice");
//      throw new IllegalStateException();

    processed = true;
    for (final SpanRule rule : rules.getSpanRules(key)) {
      if (rule.type != type)
        continue;

      final Function<Object,Object> match = SpanRuleMatcher.match(rule.predicate, value);
      if (match != null) {
        processMatch(rule, match, customizer);
        return true;
      }
    }

    return false;
  }

  private void replaceLog(final Map<String,?> fields, final SpanRule matchedRule, final Function<Object,Object> match, final LogFieldCustomizer builder) {
    for (final Map.Entry<String,?> entry : fields.entrySet()) {
      final String key = entry.getKey();
      final Object value = entry.getValue();
      if (key.equals(matchedRule.key))
        processMatch(matchedRule, match, builder);
      else if (!processRules(SpanRuleType.LOG, key, value, builder))
        builder.addLogField(key, value);
    }

    builder.log();
  }

  private static void processMatch(final SpanRule rule, final Function<Object,Object> match, final SpanCustomizer customizer) {
    if (rule.outputs != null) {
      for (final SpanRuleOutput output : rule.outputs) {
        final Object outputValue = match.apply(output.value);
        final String key = output.key != null ? output.key : rule.key;
        output.type.apply(customizer, key, outputValue);
      }
    }
  }
}