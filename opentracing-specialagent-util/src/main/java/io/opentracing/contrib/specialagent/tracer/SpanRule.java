package io.opentracing.contrib.specialagent.tracer;

class SpanRule {
  final SpanRuleType type;
  final String key;
  final Object predicate;
  final SpanRuleOutput[] outputs;

  public SpanRule(final Object predicate, final SpanRuleType type, final String key, final SpanRuleOutput[] outputs) {
    this.predicate = predicate;
    this.type = type;
    this.key = key;
    this.outputs = outputs;
  }
}