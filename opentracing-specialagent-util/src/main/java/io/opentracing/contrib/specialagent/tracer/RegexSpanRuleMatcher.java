package io.opentracing.contrib.specialagent.tracer;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RegexSpanRuleMatcher implements SpanRuleMatcher {

  private final Pattern pattern;

  public RegexSpanRuleMatcher(String valueRegex) {
    this.pattern = Pattern.compile(valueRegex);
  }

  @Override
  public SpanRuleMatch match(final Object value) {
    final Matcher matcher = pattern.matcher(value.toString());

    if (!matcher.matches()) {
      return null;
    }

    return new SpanRuleMatch() {
      @Override
      public Object getValue(Object outputExpression) {
        if (outputExpression != null) {
          return matcher.replaceAll(outputExpression.toString());
        }
        return value;
      }
    };
  }
}
