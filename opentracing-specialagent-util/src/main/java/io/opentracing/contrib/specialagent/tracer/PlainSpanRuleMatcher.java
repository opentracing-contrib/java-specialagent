package io.opentracing.contrib.specialagent.tracer;

public class PlainSpanRuleMatcher implements SpanRuleMatcher {
  private final Object expected;

  public PlainSpanRuleMatcher(final Object expected) {
    this.expected = expected;
  }

  @Override
  public SpanRuleMatch match(final Object value) {
    if (!matchesValue(value))
      return null;

    return new SpanRuleMatch() {
      @Override
      public Object getValue(final Object outputExpression) {
        return outputExpression != null ? outputExpression : value;
      }
    };
  }

  private boolean matchesValue(final Object value) {
    if (expected == null)
      return true;

    if (expected instanceof Number && value instanceof Number)
      return ((Number)expected).doubleValue() == ((Number)value).doubleValue();

    return expected.equals(value);
  }
}