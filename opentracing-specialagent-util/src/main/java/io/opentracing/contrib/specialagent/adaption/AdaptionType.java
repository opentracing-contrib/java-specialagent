package io.opentracing.contrib.specialagent.adaption;

import java.util.Objects;

import com.grack.nanojson.JsonObject;

enum AdaptionType {
  START("start") {
    @Override
    void adapt(final Adapter adapter, final long timestampMicroseconds, final String key, final Object value) {
      throw new UnsupportedOperationException();
    }

    @Override
    void validateInput(final AdaptionRule rule, final String subject) {
      if (rule.input.getKey() != null)
        throw new IllegalStateException(subject + ": Key for type=start must be null");

      if (rule.input.getValue() != null)
        throw new IllegalStateException(subject + ": Value for type=start must be null");
    }

    @Override
    void validateOutput(final AdaptionType type, final String matchKey, final String outputKey, final String subject) {
      throw new IllegalStateException(subject + ": type=start cannot be used as output");
    }
  },
  LOG("log") {
    @Override
    void adapt(final Adapter adapter, final long timestampMicroseconds, final String key, final Object value) {
      adapter.adaptLog(timestampMicroseconds, key, value);
    }

    @Override
    void validateInput(final AdaptionRule rule, final String subject) {
    }

    @Override
    void validateOutput(final AdaptionType type, final String matchKey, final String outputKey, final String subject) {
      if (type != AdaptionType.LOG && matchKey == null && outputKey == null)
        throw new IllegalStateException(subject + ": Missing output key for log fields");
    }
  },
  TAG("tag") {
    @Override
    void adapt(final Adapter adapter, final long timestampMicroseconds, final String key, final Object value) {
      adapter.adaptTag(key, value);
    }

    @Override
    void validateInput(final AdaptionRule rule, final String subject) {
      if (rule.input.getKey() == null)
        throw new IllegalStateException(subject + ": Tag without a key is not allowed");
    }

    @Override
    void validateOutput(final AdaptionType type, final String matchKey, final String outputKey, final String subject) {
      if (matchKey == null && outputKey == null)
        throw new IllegalStateException(subject + ": Missing output key for tag");
    }
  },
  OPERATION_NAME("operationName") {
    @Override
    void adapt(final Adapter adapter, final long timestampMicroseconds, final String key, final Object value) {
      adapter.adaptOperationName(value.toString());
    }

    @Override
    void validateInput(final AdaptionRule rule, final String subject) {
      if (rule.input.getKey() != null)
        throw new IllegalStateException(subject + ": Key for operationName must be null");
    }

    @Override
    void validateOutput(final AdaptionType type, final String matchKey, final String outputKey, final String subject) {
      if (outputKey != null)
        throw new IllegalStateException(subject + ": operationName cannot have output key");
    }
  };

  private final String tagName;

  AdaptionType(final String tagName) {
    this.tagName = tagName;
  }

  void validate(final AdaptionRule rule, final String subject) {
    validateInput(rule, subject);
    if (rule.outputs != null) {
      for (int i = 0; i < rule.outputs.length; ++i) {
        final Adaption output = rule.outputs[i];
        output.getType().validateOutput(rule.input.getType(), rule.input.getKey(), output.getKey(), subject + ".outputs[" + i + "]");
      }
    }
  }

  abstract void adapt(Adapter adapter, long timestampMicroseconds, String key, Object value);
  abstract void validateOutput(AdaptionType type, String matchKey, String outputKey, String subject);
  abstract void validateInput(AdaptionRule rule, String subject);

  private static AdaptionType fromString(final String str) {
    for (final AdaptionType value : values())
      if (value.tagName.equals(str))
        return value;

    return null;
  }

  static AdaptionType parseType(final JsonObject type, final String subject) {
    return Objects.requireNonNull(AdaptionType.fromString(Objects.requireNonNull(type.getString("type"), subject + ".type: Is not a string")), subject + ": Invalid type");
  }
}