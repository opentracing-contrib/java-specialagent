package io.opentracing.contrib.specialagent.adaption;

public enum AdaptionRuleType {
  LOG("log") {
    @Override
    void apply(final Adapter adapter, final String key, final Object value) {
      adapter.addLogField(key, value);
    }

    @Override
    void validateRule(final AdaptionRule<?> rule, final String subject) {
    }

    @Override
    void validateOutputKey(final AdaptionRuleType matchType, final String matchKey, final String outputKey, final String subject) {
      final boolean matchLogEvent = matchType == AdaptionRuleType.LOG && matchKey == null;
      if (!matchLogEvent && matchKey == null && outputKey == null)
        throw new IllegalStateException(subject + "missing output key for log fields");
    }
  },
  SERVICE_NAME("serviceName") {
    @Override
    void apply(final Adapter adapter, final String key, final Object value) {
      throw new IllegalStateException();
    }

    @Override
    void validateRule(final AdaptionRule<?> rule, final String subject) {
      if (rule.key != null)
        throw new IllegalStateException(subject + "key for serviceName must be null");
    }

    @Override
    void validateOutputKey(final AdaptionRuleType matchType, final String matchKey, final String outputKey, final String subject) {
      throw new IllegalStateException(subject + "serviceName cannot be used as output");
    }
  },
  OPERATION_NAME("operationName") {
    @Override
    void apply(final Adapter adapter, final String key, final Object value) {
      adapter.setOperationName(value.toString());
    }

    @Override
    void validateRule(final AdaptionRule<?> rule, final String subject) {
      if (rule.key != null)
        throw new IllegalStateException(subject + "key for operationName must be null");
    }

    @Override
    void validateOutputKey(final AdaptionRuleType matchType, final String matchKey, final String outputKey, final String subject) {
      if (outputKey != null)
        throw new IllegalStateException(subject + "operationName cannot have output key");
    }
  },
  TAG("tag") {
    @Override
    void apply(final Adapter adapter, final String key, final Object value) {
      adapter.setTag(key, value);
    }

    @Override
    void validateRule(final AdaptionRule<?> rule, final String subject) {
      if (rule.key == null)
        throw new IllegalStateException(subject + "tag without a key is not allowed");
    }

    @Override
    void validateOutputKey(final AdaptionRuleType matchType, final String matchKey, final String outputKey, final String subject) {
      if (matchKey == null && outputKey == null)
        throw new IllegalStateException(subject + "missing output key for tag");
    }
  };

  private final String tagName;

  private AdaptionRuleType(final String tagName) {
    this.tagName = tagName;
  }

  void validate(final AdaptionRule<?> rule, final String subject) {
    validateRule(rule, subject);
    if (rule.outputs != null) {
      for (int i = 0; i < rule.outputs.length; ++i) {
        final AdaptedOutput output = rule.outputs[i];
        output.type.validateOutputKey(rule.type, rule.key, output.key, subject + "output " + i + ": ");
      }
    }
  }

  abstract void apply(Adapter adapter, String key, Object value);
  abstract void validateOutputKey(AdaptionRuleType matchType, String matchKey, String outputKey, String subject);
  abstract void validateRule(AdaptionRule<?> rule, String subject);

  public static AdaptionRuleType fromString(final String str) {
    for (final AdaptionRuleType value : values())
      if (value.tagName.equals(str))
        return value;

    return null;
  }
}
