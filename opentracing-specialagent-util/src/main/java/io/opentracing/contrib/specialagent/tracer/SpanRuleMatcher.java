package io.opentracing.contrib.specialagent.tracer;

public interface SpanRuleMatcher {
  /**
   * returns {@code null} if not matched
   */
  SpanRuleMatch match(Object value);
}
