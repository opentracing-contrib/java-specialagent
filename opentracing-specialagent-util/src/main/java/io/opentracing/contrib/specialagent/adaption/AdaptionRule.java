package io.opentracing.contrib.specialagent.adaption;

abstract class AdaptionRule<T,V> {
  final AdaptionType type;
  final String key;
  final AdaptedOutput[] outputs;

  AdaptionRule(final AdaptionType type, final String key, final AdaptedOutput[] outputs) {
    this.type = type;
    this.key = key;
    this.outputs = outputs;
  }

  abstract T getPredicate();
  abstract V match(Object input);
  abstract Object adapt(V match, Object input, Object output);

  final void validate(final String subject) {
    type.validate(this, subject);
  }
}