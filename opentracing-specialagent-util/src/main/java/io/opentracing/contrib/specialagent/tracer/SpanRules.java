package io.opentracing.contrib.specialagent.tracer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.opentracing.contrib.specialagent.Function;

public class SpanRules {
  private final Map<String,List<SpanRule>> keyToRules = new LinkedHashMap<>();

  public SpanRules(final SpanRule[] rules) {
    for (final SpanRule rule : rules) {
      final String key = rule.key;
      List<SpanRule> list = keyToRules.get(key);
      if (list == null)
        keyToRules.put(key, list = new ArrayList<>());

      list.add(rule);
    }
  }

  public void processOperationName(final String operationName, final SpanCustomizer customizer) {
    if (!processRules(SpanRuleType.OPERATION_NAME, null, operationName, customizer))
      customizer.setOperationName(operationName);
  }

  public void setTag(final String key, final Object value, final SpanCustomizer customizer) {
    if (!processRules(SpanRuleType.TAG, key, value, customizer))
      customizer.setTag(key, value);
  }

  public void log(final String event, final LogEventCustomizer builder) {
    if (!processRules(SpanRuleType.LOG, null, event, builder))
      builder.log(event);
  }

  public void log(final Map<String,?> fields, final LogFieldCustomizer builder) {
    for (final Map.Entry<String,?> entry : fields.entrySet()) {
      final String key = entry.getKey();
      final Object value = entry.getValue();

      for (final SpanRule rule : getSpanRules(key)) {
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

  private List<SpanRule> getSpanRules(final String key) {
    final List<SpanRule> rules = keyToRules.get(key);
    return rules != null ? rules : Collections.<SpanRule>emptyList();
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

  private boolean processRules(final SpanRuleType type, final String key, final Object value, final SpanCustomizer customizer) {
    for (final SpanRule rule : getSpanRules(key)) {
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