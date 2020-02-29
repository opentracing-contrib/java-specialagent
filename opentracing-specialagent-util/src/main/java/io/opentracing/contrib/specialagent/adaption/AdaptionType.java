package io.opentracing.contrib.specialagent.adaption;

enum AdaptionType {
  LOG("log") {
    @Override
    void adapt(final Adapter adapter, final long timestampMicroseconds, final String key, final Object value) {
      adapter.adaptLog(timestampMicroseconds, key, value);
    }

    @Override
    void validateRule(final AdaptionRule<?,?> rule, final String subject) {
    }

    @Override
    void validateOutputKey(final AdaptionType type, final String matchKey, final String outputKey, final String subject) {
      if (type != AdaptionType.LOG && matchKey == null && outputKey == null)
        throw new IllegalStateException(subject + "missing output key for log fields");
    }
  },
  SPAN("span") {
    @Override
    void adapt(final Adapter adapter, final long timestampMicroseconds, final String key, final Object value) {
      throw new UnsupportedOperationException();
    }

    @Override
    void validateRule(final AdaptionRule<?,?> rule, final String subject) {
      if (rule.key != null)
        throw new IllegalStateException(subject + "key for span must be null");
      if (rule.getPredicate() != null)
        throw new IllegalStateException(subject + "value and valueRegex for span must be null");
    }

    @Override
    void validateOutputKey(final AdaptionType type, final String matchKey, final String outputKey, final String subject) {
      throw new IllegalStateException(subject + "span cannot be used as output");
    }
  },
  OPERATION_NAME("operationName") {
    @Override
    void adapt(final Adapter adapter, final long timestampMicroseconds, final String key, final Object value) {
      adapter.adaptOperationName(value.toString());
    }

    @Override
    void validateRule(final AdaptionRule<?,?> rule, final String subject) {
      if (rule.key != null)
        throw new IllegalStateException(subject + "key for operationName must be null");
    }

    @Override
    void validateOutputKey(final AdaptionType type, final String matchKey, final String outputKey, final String subject) {
      if (outputKey != null)
        throw new IllegalStateException(subject + "operationName cannot have output key");
    }
  },
  TAG("tag") {
    @Override
    void adapt(final Adapter adapter, final long timestampMicroseconds, final String key, final Object value) {
      adapter.adaptTag(key, value);
    }

    @Override
    void validateRule(final AdaptionRule<?,?> rule, final String subject) {
      if (rule.key == null)
        throw new IllegalStateException(subject + "tag without a key is not allowed");
    }

    @Override
    void validateOutputKey(final AdaptionType type, final String matchKey, final String outputKey, final String subject) {
      if (matchKey == null && outputKey == null)
        throw new IllegalStateException(subject + "missing output key for tag");
    }
  };

  private final String tagName;

  AdaptionType(final String tagName) {
    this.tagName = tagName;
  }

  void validate(final AdaptionRule<?,?> rule, final String subject) {
    validateRule(rule, subject);
    if (rule.outputs != null) {
      for (int i = 0; i < rule.outputs.length; ++i) {
        final AdaptedOutput output = rule.outputs[i];
        output.type.validateOutputKey(rule.type, rule.key, output.key, subject + "output " + i + ": ");
      }
    }
  }

  abstract void adapt(Adapter adapter, long timestampMicroseconds, String key, Object value);
  abstract void validateOutputKey(AdaptionType type, String matchKey, String outputKey, String subject);
  abstract void validateRule(AdaptionRule<?,?> rule, String subject);

  static AdaptionType fromString(final String str) {
    for (final AdaptionType value : values())
      if (value.tagName.equals(str))
        return value;

    return null;
  }
}
