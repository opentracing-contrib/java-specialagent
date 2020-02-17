package io.opentracing.contrib.specialagent.tracer;

public class PlainSpanRuleMatcher implements SpanRuleMatcher {

  private final Object expected;

  public PlainSpanRuleMatcher(Object expected) {
    this.expected = expected;
  }

  @Override
  public SpanRuleMatch match(final Object value) {
    if (!matchesValue(value)) {
      return null;
    }

    return new SpanRuleMatch() {
      @Override
      public Object getValue(Object outputExpression) {
        if (outputExpression != null) {
          return outputExpression;
        }
        return value;
      }
    };
  }

  private boolean matchesValue(Object value) {
    if (expected == null) {
      return true;
    }
    if (expected instanceof Number && value instanceof Number) {
      return ((Number) expected).doubleValue() == ((Number) value).doubleValue();
    }
    return expected.equals(value);
  }
}
