package io.opentracing.contrib.specialagent.adaption;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;

final class AdaptionRules {
  final LinkedHashMap<String,List<AdaptionRule>> keyToRules = new LinkedHashMap<>();

  void add(final AdaptionRule rule) {
    List<AdaptionRule> list = keyToRules.get(rule.input.getKey());
    if (list == null)
      keyToRules.put(rule.input.getKey(), list = new ArrayList<>());

    list.add(rule);
  }

  void addAll(final AdaptionRules rules) {
    for (final List<AdaptionRule> value : rules.keyToRules.values())
      for (final AdaptionRule rule : value)
        add(rule);
  }

  List<AdaptionRule> getRules(final String key) {
    final List<AdaptionRule> inputs = keyToRules.get(key);
    return inputs != null ? inputs : Collections.<AdaptionRule>emptyList();
  }
}