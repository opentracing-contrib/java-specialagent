package io.opentracing.contrib.specialagent.tracer;

public interface SpanRuleMatcher {
  /**
   * Returns the {@link SpanRuleMatch}, or {@code null} if not matched.
   *
   * @param value The value to match.
   * @return The {@link SpanRuleMatch}, or {@code null} if not matched.
   */
  SpanRuleMatch match(final Object value);
}