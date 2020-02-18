package io.opentracing.contrib.specialagent.tracer;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SpanRulePattern {

  private final Pattern pattern;

  public SpanRulePattern(Pattern pattern) {
    this.pattern = pattern;
  }

  Matcher matcher(String input) {
    return pattern.matcher(input);
  }
}
