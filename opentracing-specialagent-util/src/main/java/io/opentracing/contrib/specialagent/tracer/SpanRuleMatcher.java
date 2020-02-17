package io.opentracing.contrib.specialagent.tracer;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.opentracing.contrib.specialagent.Function;

class SpanRuleMatcher {
  private static boolean matchesSimpleValue(final Object predicate, final Object value) {
    if (predicate == null)
      return true;

    if (predicate instanceof Number && value instanceof Number)
      return ((Number)predicate).doubleValue() == ((Number)value).doubleValue();

    return predicate.equals(value);
  }

  static Function<Object,Object> match(final Object predicate, final Object value) {
    if (predicate instanceof Pattern) {
      final Matcher matcher = ((Pattern)predicate).matcher(value.toString());
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