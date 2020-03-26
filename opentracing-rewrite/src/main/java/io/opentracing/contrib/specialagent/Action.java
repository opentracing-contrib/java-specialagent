/* Copyright 2020 The OpenTracing Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentracing.contrib.specialagent;

import java.util.Objects;
import java.util.regex.Pattern;

import com.grack.nanojson.JsonObject;

abstract class Action {
  static Action parseInputEvent(final JsonObject object, final String subject) {
    return parseEvent(object, subject, true);
  }

  static Action parseOutputEvent(final JsonObject object, final String subject) {
    return parseEvent(object, subject, false);
  }

  private static Action parseEvent(final JsonObject object, final String subject, final boolean isInput) {
    Objects.requireNonNull(object, subject + ": Not an object");
    final String type = Objects.requireNonNull(object.getString("type"), subject + ".type: Is not a string");
    final String key = object.getString("key");
    Object value = object.get("value");
    if (isInput && value instanceof String)
      value = Pattern.compile((String)value);

    if ("log".equals(type))
      return new Log(key, value);

    if ("tag".equals(type))
      return new Tag(key, value);

    if ("operationName".equals(type))
      return new OperationName(key, value);

    throw new IllegalStateException(subject + ": Invalid type");
  }

  private final String key;
  private final Object value;

  private Action(final String key, final Object value) {
    this.key = key;
    this.value = value;
  }

  String getKey() {
    return this.key;
  }

  Object getValue() {
    return this.value;
  }

  abstract void rewrite(Rewriter rewriter, long timestampMicroseconds, String key, Object value);
  abstract void validateOutput(Action input, String subject);
  abstract void validateInput(RewriteRule rule, String subject);

  void validate(final RewriteRule rule, final String subject) {
    validateInput(rule, subject);
    if (rule.outputs != null) {
      for (int i = 0; i < rule.outputs.length; ++i) {
        final Action output = rule.outputs[i];
        output.validateOutput(rule.input, subject + ".output[" + i + "]");
      }
    }
  }

  static class OperationName extends Action {
    private OperationName(final String key, final Object value) {
      super(key, value);
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
    void validateOutput(final Action input, final String subject) {
      if (getKey() != null)
        throw new IllegalStateException(subject + ": operationName cannot have output key");
    }
  }

  static class Log extends Action {
    private Log(final String key, final Object value) {
      super(key, value);
    }

    @Override
    void rewrite(final Rewriter rewriter, final long timestampMicroseconds, final String key, final Object value) {
      rewriter.rewriteLog(timestampMicroseconds, key, value);
    }

    @Override
    void validateInput(final RewriteRule rule, final String subject) {
    }

    @Override
    void validateOutput(final Action input, final String subject) {
      if (input.getClass() != Action.Log.class && input.getKey() == null && getKey() == null)
        throw new IllegalStateException(subject + ": Missing output key for log fields");
    }
  }

  static class Tag extends Action {
    private Tag(final String key, final Object value) {
      super(key, value);
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
    void validateOutput(final Action input, final String subject) {
      if (input.getKey() == null && getKey() == null)
        throw new IllegalStateException(subject + ": Missing output key for tag");
    }
  }
}