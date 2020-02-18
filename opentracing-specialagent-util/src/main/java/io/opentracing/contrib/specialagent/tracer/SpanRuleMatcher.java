package io.opentracing.contrib.specialagent.tracer;

import io.opentracing.contrib.specialagent.Function;

import java.util.regex.Matcher;

public class SpanRuleMatcher {

  private static boolean matchesSimpleValue(final Object predicate, final Object value) {
    if (predicate == null)
      return true;

    if (predicate instanceof Number && value instanceof Number)
      return ((Number)predicate).doubleValue() == ((Number)value).doubleValue();

    return predicate.equals(value);
  }

  static Function<Object,Object> match(final Object predicate, final Object value) {
    if (predicate instanceof SpanRulePattern) {
      final Matcher matcher = ((SpanRulePattern)predicate).matcher(value.toString());
      if (!matcher.matches())
        return null;

      return new Function<Object,Object>() {
        @Override
        public Object apply(final Object outputExpression) {
          return outputExpression != null ? matcher.replaceAll(outputExpression.toString()) : value;
        }
      };
    }

    if (!matchesSimpleValue(predicate, value))
      return null;

    return new Function<Object,Object>() {
      @Override
      public Object apply(final Object outputExpression) {
        return outputExpression != null ? outputExpression : value;
      }
    };
  }
}
