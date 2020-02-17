package io.opentracing.contrib.specialagent.tracer;

public enum SpanRuleType {
  LOG("log") {
    @Override
    void apply(final SpanCustomizer customizer, final String key, final Object value) {
      customizer.addLogField(key, value);
    }

    @Override
    void validateRule(final SpanRule rule, final String subject) {
    }

    @Override
    void validateOutputKey(final SpanRuleType matchType, final String matchKey, final String outputKey, final String subject) {
      final boolean matchLogEvent = matchType == SpanRuleType.LOG && matchKey == null;
      if (!matchLogEvent && matchKey == null && outputKey == null)
        throw new IllegalStateException(subject + "missing output key for log fields");
    }
  },
  OPERATION_NAME("operationName") {
    @Override
    void apply(final SpanCustomizer customizer, final String key, final Object value) {
      customizer.setOperationName(value.toString());
    }

    @Override
    void validateRule(final SpanRule rule, final String subject) {
      if (rule.key != null)
        throw new IllegalStateException(subject + "key for operationName must be null");
    }

    @Override
    void validateOutputKey(final SpanRuleType matchType, final String matchKey, final String outputKey, final String subject) {
      if (outputKey != null)
        throw new IllegalStateException(subject + "operationName cannot have output key");
    }
  },
  TAG("tag") {
    @Override
    void apply(final SpanCustomizer customizer, final String key, final Object value) {
      customizer.setTag(key, value);
    }

    @Override
    void validateRule(final SpanRule rule, final String subject) {
      if (rule.key == null)
        throw new IllegalStateException(subject + "tag without a key is not allowed");
    }

    @Override
    void validateOutputKey(final SpanRuleType matchType, final String matchKey, final String outputKey, final String subject) {
      if (matchKey == null && outputKey == null)
        throw new IllegalStateException(subject + "missing output key for tag");
    }
  };

  private final String tagName;

  private SpanRuleType(final String tagName) {
    this.tagName = tagName;
  }

  void validate(final SpanRule rule, final String subject) {
    validateRule(rule, subject);
    for (int i = 0, size = rule.outputs.size(); i < size; ++i) {
      final SpanRuleOutput output = rule.outputs.get(i);
      output.type.validateOutputKey(rule.type, rule.key, output.key, subject + "output " + i + ": ");
    }
  }

  abstract void apply(SpanCustomizer customizer, String key, Object value);
  abstract void validateOutputKey(SpanRuleType matchType, String matchKey, String outputKey, String subject);
  abstract void validateRule(SpanRule rule, String subject);

  public static SpanRuleType fromString(final String str) {
    for (final SpanRuleType value : values())
      if (value.tagName.equals(str))
        return value;

    return null;
  }
}