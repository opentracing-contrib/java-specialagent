package io.opentracing.contrib.specialagent.tracer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SpanRules {
  private final Map<String,List<SpanRule>> rulesByKey = new LinkedHashMap<>();

  public SpanRules(final List<SpanRule> rules) {
    for (final SpanRule rule : rules) {
      final String key = rule.getKey();
      List<SpanRule> list = rulesByKey.get(key);
      if (list == null)
        rulesByKey.put(key, list = new ArrayList<>());

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
        if (rule.getType() != SpanRuleType.LOG)
          continue;

        final SpanRuleMatch match = rule.getMatcher().match(value);
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
    final List<SpanRule> rules = rulesByKey.get(key);
    return rules != null ? rules : Collections.<SpanRule>emptyList();
  }

  private void replaceLog(final Map<String,?> fields, final SpanRule matchedRule, final SpanRuleMatch match, final LogFieldCustomizer builder) {
    for (final Map.Entry<String,?> entry : fields.entrySet()) {
      final String key = entry.getKey();
      final Object value = entry.getValue();
      if (key.equals(matchedRule.getKey()))
        processMatch(matchedRule, match, builder);
      else if (!processRules(SpanRuleType.LOG, key, value, builder))
        builder.addLogField(key, value);
    }

    builder.finish();
  }

  private boolean processRules(final SpanRuleType type, final String key, final Object value, final SpanCustomizer customizer) {
    for (final SpanRule rule : getSpanRules(key)) {
      if (rule.getType() != type)
        continue;

      final SpanRuleMatch match = rule.getMatcher().match(value);
      if (match != null) {
        processMatch(rule, match, customizer);
        return true;
      }
    }

    return false;
  }

  private static void processMatch(final SpanRule rule, final SpanRuleMatch match, final SpanCustomizer customizer) {
    for (final SpanRuleOutput output : rule.getOutputs()) {
      final Object outputValue = match.getValue(output.getValue());
      final String key = output.getKey() != null ? output.getKey() : rule.getKey();
      output.getType().apply(customizer, key, outputValue);
    }
  }
}