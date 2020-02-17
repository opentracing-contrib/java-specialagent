package io.opentracing.contrib.specialagent.tracer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SpanRules {
  private Map<String, List<SpanRule>> rulesByKey = new LinkedHashMap<>();

  public SpanRules(List<SpanRule> rules) {
    for (SpanRule rule : rules) {
      String key = rule.getKey();
      List<SpanRule> list = rulesByKey.get(key);
      if (list == null) {
        list = new ArrayList<>();
        rulesByKey.put(key, list);
      }
      list.add(rule);
    }
  }

  public void processOperationName(String operationName, SpanCustomizer customizer) {
    if (!processRules(SpanRuleType.operationName, null, operationName, customizer)) {
      customizer.setOperationName(operationName);
    }
  }

  public void setTag(String key, Object value, SpanCustomizer customizer) {
    if (!processRules(SpanRuleType.tag, key, value, customizer)) {
      customizer.setTag(key, value);
    }
  }

  public void log(String event, LogEventCustomizer builder) {
    if (!processRules(SpanRuleType.log, null, event, builder)) {
      builder.log(event);
    }
  }

  public void log(Map<String, ?> fields, LogFieldCustomizer builder) {
    for (Map.Entry<String, ?> entry : fields.entrySet()) {
      String key = entry.getKey();
      Object value = entry.getValue();

      for (SpanRule rule : getSpanRules(key)) {
        if (rule.getType() != SpanRuleType.log) {
          continue;
        }

        SpanRuleMatch match = rule.getMatcher().match(value);
        if (match != null) {
          replaceLog(fields, rule, match, builder);
          return;
        }
      }
    }

    //nothing matched
    builder.log(fields);
  }

  private List<SpanRule> getSpanRules(String key) {
    List<SpanRule> rules = rulesByKey.get(key);
    if (rules == null) {
      return Collections.emptyList();
    }
    return rules;
  }

  private void replaceLog(Map<String, ?> fields, SpanRule matchedRule, SpanRuleMatch match, LogFieldCustomizer builder) {

    for (Map.Entry<String, ?> entry : fields.entrySet()) {
      String key = entry.getKey();
      Object value = entry.getValue();

      if (key.equals(matchedRule.getKey())) {
        processMatch(matchedRule, match, builder);
      } else {
        if (!processRules(SpanRuleType.log, key, value, builder)) {
          builder.addLogField(key, value);
        }
      }
    }

    builder.finish();
  }

  private boolean processRules(SpanRuleType type, String key, Object value, SpanCustomizer customizer) {
    for (SpanRule rule : getSpanRules(key)) {
      if (rule.getType() != type) {
        continue;
      }

      SpanRuleMatch match = rule.getMatcher().match(value);
      if (match != null) {
        processMatch(rule, match, customizer);
        return true;
      }
    }
    return false;
  }

  private void processMatch(SpanRule rule, SpanRuleMatch match, SpanCustomizer customizer) {
    for (SpanRuleOutput output : rule.getOutputs()) {
      Object outputValue = match.getValue(output.getValue());
      String key = output.getKey() != null ? output.getKey() : rule.getKey();
      output.getType().apply(customizer, key, outputValue);
    }
  }

}
