package io.opentracing.contrib.specialagent.tracer;

import java.util.List;

class SpanRule {
  final SpanRuleType type;
  final String key;
  final Object predicate;
  final List<SpanRuleOutput> outputs;

  public SpanRule(final Object predicate, final SpanRuleType type, final String key, final List<SpanRuleOutput> outputs) {
    this.predicate = predicate;
    this.type = type;
    this.key = key;
    this.outputs = outputs;
  }
}