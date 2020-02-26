package io.opentracing.contrib.specialagent.adaption;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.opentracing.contrib.specialagent.Function;

class PatternAdaptionRule extends AdaptionRule<Pattern> {
  private final Pattern pattern;

  PatternAdaptionRule(final Pattern pattern, final AdaptionRuleType type, final String key, final AdaptedOutput[] outputs) {
    super(type, key, outputs);
    this.pattern = pattern;
  }

  @Override
  Pattern getPredicate() {
    return pattern;
  }

  @Override
  Function<Object,Object> match(final Object value) {
    final Matcher matcher = pattern.matcher(value.toString());
    if (!matcher.matches())
      return null;

    return new Function<Object,Object>() {
      @Override
      public Object apply(final Object outputExpression) {
        return outputExpression != null ? matcher.replaceAll(outputExpression.toString()) : value;
      }
    };
  }
}