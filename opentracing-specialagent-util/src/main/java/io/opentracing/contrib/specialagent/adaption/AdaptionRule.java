package io.opentracing.contrib.specialagent.adaption;

import io.opentracing.contrib.specialagent.Function;

abstract class AdaptionRule<T> {
  final AdaptionRuleType type;
  final String key;
  final AdaptedOutput[] outputs;

  AdaptionRule(final AdaptionRuleType type, final String key, final AdaptedOutput[] outputs) {
    this.type = type;
    this.key = key;
    this.outputs = outputs;
  }

  abstract T getPredicate();
  abstract Function<Object,Object> match(Object value);

  final void validate(final String subject) {
    type.validate(this, subject);
  }
}