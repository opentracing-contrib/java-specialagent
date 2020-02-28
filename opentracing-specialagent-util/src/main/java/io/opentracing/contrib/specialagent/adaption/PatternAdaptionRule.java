package io.opentracing.contrib.specialagent.adaption;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

class PatternAdaptionRule extends AdaptionRule<Pattern,Matcher> {
  private final Pattern pattern;

  PatternAdaptionRule(final Pattern pattern, final AdaptionType type, final String key, final AdaptedOutput[] outputs) {
    super(type, key, outputs);
    this.pattern = pattern;
  }

  @Override
  Pattern getPredicate() {
    return pattern;
  }

  @Override
  Matcher match(final Object input) {
    final Matcher matcher = pattern.matcher(input.toString());
    return matcher.matches() ? matcher : null;
  }

  @Override
  Object adapt(final Matcher matcher, final Object value, final Object output) {
    return output != null ? matcher.replaceAll(output.toString()) : value;
  }
}