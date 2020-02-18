package io.opentracing.contrib.specialagent.tracer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;

final class SpanRules {
  private final LinkedHashMap<String,List<SpanRule>> keyToRules = new LinkedHashMap<>();

  SpanRules(final SpanRule[] rules) {
    for (final SpanRule rule : rules) {
      List<SpanRule> list = keyToRules.get(rule.key);
      if (list == null)
        keyToRules.put(rule.key, list = new ArrayList<>());

      list.add(rule);
    }
  }

  List<SpanRule> getSpanRules(final String key) {
    final List<SpanRule> rules = keyToRules.get(key);
    return rules != null ? rules : Collections.<SpanRule>emptyList();
  }
}