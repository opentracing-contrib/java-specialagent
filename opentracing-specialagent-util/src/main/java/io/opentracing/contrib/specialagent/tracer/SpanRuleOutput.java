package io.opentracing.contrib.specialagent.tracer;

public class SpanRuleOutput {
  private final SpanRuleType type;
  private final String key;
  private final Object value;

  public SpanRuleOutput(final SpanRuleType type, final String key, final Object value) {
    this.type = type;
    this.key = key;
    this.value = value;
  }

  public SpanRuleType getType() {
    return type;
  }

  public String getKey() {
    return key;
  }

  public Object getValue() {
    return value;
  }
}