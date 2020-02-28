package io.opentracing.contrib.specialagent.adaption;

class SimpleAdaptionRule extends AdaptionRule<Object,Boolean> {
  private static boolean matchesSimpleValue(final Object predicate, final Object value) {
    if (predicate == null)
      return true;

    if (predicate instanceof Number && value instanceof Number)
      return ((Number)predicate).doubleValue() == ((Number)value).doubleValue();

    return predicate.equals(value);
  }

  private final Object value;

  SimpleAdaptionRule(final Object value, final AdaptionType type, final String key, final AdaptedOutput[] outputs) {
    super(type, key, outputs);
    this.value = value;
  }

  @Override
  Object getPredicate() {
    return value;
  }

  @Override
  Boolean match(final Object input) {
    return matchesSimpleValue(this.value, input) ? Boolean.TRUE : null;
  }

  @Override
  Object adapt(final Boolean match, final Object input, final Object output) {
    return output != null ? output : input;
  }
}