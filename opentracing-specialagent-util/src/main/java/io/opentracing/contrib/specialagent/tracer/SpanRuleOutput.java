package io.opentracing.contrib.specialagent.tracer;

class SpanRuleOutput {
  final SpanRuleType type;
  final String key;
  final Object value;

  public SpanRuleOutput(final SpanRuleType type, final String key, final Object value) {
    this.type = type;
    this.key = key;
    this.value = value;
  }
}