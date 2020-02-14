package io.opentracing.contrib.specialagent.tracer;

public interface SpanRuleMatch {
  Object getValue(Object outputExpression);
}
