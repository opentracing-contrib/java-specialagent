package io.opentracing.contrib.specialagent.tracer;

import java.util.List;

public class SpanRule {
  private final SpanRuleType type;
  private final String key;
  private final SpanRuleMatcher matcher;
  private final List<SpanRuleOutput> outputs;

  public SpanRule(SpanRuleMatcher matcher, SpanRuleType type, String key, List<SpanRuleOutput> outputs) {
    this.matcher = matcher;
    this.type = type;
    this.key = key;
    this.outputs = outputs;
  }

  public List<SpanRuleOutput> getOutputs() {
    return outputs;
  }

  public SpanRuleMatcher getMatcher() {
    return matcher;
  }

  public SpanRuleType getType() {
    return type;
  }

  public String getKey() {
    return key;
  }
}
