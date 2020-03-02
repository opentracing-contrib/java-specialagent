package io.opentracing.contrib.specialagent.rewrite;

import java.util.Objects;
import java.util.regex.Pattern;

import com.grack.nanojson.JsonObject;

abstract class Event {
  static Event parseInputEvent(final JsonObject object, final String subject) {
    return parseEvent(object, subject, true);
  }

  static Event parseOutputEvent(final JsonObject object, final String subject) {
    return parseEvent(object, subject, false);
  }

  private static Event parseEvent(final JsonObject object, final String subject, final boolean isInput) {
    Objects.requireNonNull(object, subject + ": Not an object");
    final String type = Objects.requireNonNull(object.getString("type"), subject + ".type: Is not a string");
    final String key = object.getString("key");
    Object value = object.get("value");
    if (isInput && value instanceof String)
      value = Pattern.compile((String)value);

    if ("start".equals(type))
      return new Start(key, value);

    if ("log".equals(type))
      return new Log(key, value);

    if ("tag".equals(type))
      return new Tag(key, value);

    if ("operationName".equals(type))
      return new OperationName(key, value);

    throw new IllegalStateException(subject + ": Invalid type");
  }

  private final String key;
  Object value;

  private Event(final String key, final Object value) {
    this.key = key;
    this.value = value;
  }

  String getKey() {
    return this.key;
  }

  Object getValue() {
    return this.value;
  }

  abstract String getTagName();
  abstract void rewrite(Rewriter rewriter, long timestampMicroseconds, String key, Object value);
  abstract void validateOutput(Event input, String subject);
  abstract void validateInput(RewriteRule rule, String subject);

  void validate(final RewriteRule rule, final String subject) {
    validateInput(rule, subject);
    if (rule.outputs != null) {
      for (int i = 0; i < rule.outputs.length; ++i) {
        final Event output = rule.outputs[i];
        output.validateOutput(rule.input, subject + ".output[" + i + "]");
      }
    }
  }

  static class Start extends Event {
    private Start(final String key, final Object value) {
      super(key, value);
    }

    @Override
    String getTagName() {
      return "start";
    }

    @Override
    void rewrite(final Rewriter rewriter, final long timestampMicroseconds, final String key, final Object value) {
      throw new UnsupportedOperationException();
    }

    @Override
    void validateInput(final RewriteRule rule, final String subject) {
      if (rule.input.getKey() != null)
        throw new IllegalStateException(subject + ": Key for type=start must be null");

      if (rule.input.getValue() != null)
        throw new IllegalStateException(subject + ": Value for type=start must be null");
    }

    @Override
    void validateOutput(final Event input, final String subject) {
      throw new IllegalStateException(subject + ": type=start cannot be used as output");
    }
  }

  static class OperationName extends Event {
    private OperationName(final String key, final Object value) {
      super(key, value);
    }

    @Override
    String getTagName() {
      return "operationName";
    }

    @Override
    void rewrite(final Rewriter rewriter, final long timestampMicroseconds, final String key, final Object value) {
      rewriter.rewriteOperationName(value.toString());
    }

    @Override
    void validateInput(final RewriteRule rule, final String subject) {
      if (rule.input.getKey() != null)
        throw new IllegalStateException(subject + ": Key for operationName must be null");
    }

    @Override
    void validateOutput(final Event input, final String subject) {
      if (getKey() != null)
        throw new IllegalStateException(subject + ": operationName cannot have output key");
    }
  }

  static class Log extends Event {
    private Log(final String key, final Object value) {
      super(key, value);
    }

    @Override
    String getTagName() {
      return "log";
    }

    @Override
    void rewrite(final Rewriter rewriter, final long timestampMicroseconds, final String key, final Object value) {
      rewriter.rewriteLog(timestampMicroseconds, key, value);
    }

    @Override
    void validateInput(final RewriteRule rule, final String subject) {
    }

    @Override
    void validateOutput(final Event input, final String subject) {
      if (input.getClass() != Event.Log.class && input.getKey() == null && getKey() == null)
        throw new IllegalStateException(subject + ": Missing output key for log fields");
    }
  }

  static class Tag extends Event {
    private Tag(final String key, final Object value) {
      super(key, value);
    }

    @Override
    String getTagName() {
      return "tag";
    }

    @Override
    void rewrite(final Rewriter rewriter, final long timestampMicroseconds, final String key, final Object value) {
      rewriter.rewriteTag(key, value);
    }

    @Override
    void validateInput(final RewriteRule rule, final String subject) {
      if (rule.input.getKey() == null)
        throw new IllegalStateException(subject + ": Tag without a key is not allowed");
    }

    @Override
    void validateOutput(final Event input, final String subject) {
      if (input.getKey() == null && getKey() == null)
        throw new IllegalStateException(subject + ": Missing output key for tag");
    }
  }
}