package io.opentracing.contrib.specialagent.adaption;

import io.opentracing.contrib.specialagent.Function;

class SimpleAdaptionRule extends AdaptionRule<Object> {
  private static boolean matchesSimpleValue(final Object predicate, final Object value) {
    if (predicate == null)
      return true;

    if (predicate instanceof Number && value instanceof Number)
      return ((Number)predicate).doubleValue() == ((Number)value).doubleValue();

    return predicate.equals(value);
  }

  private final Object value;

  SimpleAdaptionRule(final Object value, final AdaptionRuleType type, final String key, final AdaptedOutput[] outputs) {
    super(type, key, outputs);
    this.value = value;
  }

  @Override
  Object getPredicate() {
    return value;
  }

  @Override
  Function<Object,Object> match(final Object value) {
    if (!matchesSimpleValue(this.value, value))
      return null;

    return new Function<Object,Object>() {
      @Override
      public Object apply(final Object outputExpression) {
        return outputExpression != null ? outputExpression : value;
      }
    };
  }
}