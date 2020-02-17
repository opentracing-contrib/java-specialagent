package io.opentracing.contrib.specialagent.tracer;

public class SpanRuleValidator {
  public void validateRule(SpanRule rule, String subject) {
    switch (rule.getType()) {
      case log:
        break;
      case operationName:
        validateOperationName(rule, subject);
        break;
      case tag:
        validateTag(rule, subject);
        break;
    }

    for (int i = 0; i < rule.getOutputs().size(); i++) {
      SpanRuleOutput output = rule.getOutputs().get(i);
      validateOutputKey(rule.getType(), rule.getKey(), output.getType(), output.getKey(),
        subject + "output " + i + ": ");
    }
  }

  private void validateOperationName(SpanRule rule, String subject) {
    if (rule.getKey() != null) {
      throw new IllegalStateException(subject + "key for operationName must be null");
    }
  }

  private void validateTag(SpanRule rule, String subject) {
    if (rule.getKey() == null) {
      throw new IllegalStateException(subject + "tag without a key is not allowed");
    }
  }

  private void validateOutputKey(SpanRuleType matchType, String matchKey,
                                 SpanRuleType outputType, String outputKey, String subject) {
    boolean noKeyProvided = matchKey == null && outputKey == null;

    switch (outputType) {
      case log:
        boolean matchLogEvent = matchType == SpanRuleType.log && matchKey == null;
        if (!matchLogEvent && noKeyProvided) {
          throw new IllegalStateException(subject + "missing output key for log fields");
        }
        break;
      case operationName:
        if (outputKey != null) {
          throw new IllegalStateException(subject + "operationName cannot have output key");
        }
        break;
      case tag:
        if (noKeyProvided) {
          throw new IllegalStateException(subject + "missing output key for tag");
        }
        break;
    }
  }
}
