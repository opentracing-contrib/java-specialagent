package io.opentracing.contrib.specialagent.adaption;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;

final class AdaptionRules {
  private final LinkedHashMap<String,List<AdaptionRule<?>>> keyToRules = new LinkedHashMap<>();

  AdaptionRules(final AdaptionRule<?>[] rules) {
    for (final AdaptionRule<?> rule : rules) {
      List<AdaptionRule<?>> list = keyToRules.get(rule.key);
      if (list == null)
        keyToRules.put(rule.key, list = new ArrayList<>());

      list.add(rule);
    }
  }

  List<AdaptionRule<?>> getSpanRules(final String key) {
    final List<AdaptionRule<?>> rules = keyToRules.get(key);
    return rules != null ? rules : Collections.<AdaptionRule<?>>emptyList();
  }
}