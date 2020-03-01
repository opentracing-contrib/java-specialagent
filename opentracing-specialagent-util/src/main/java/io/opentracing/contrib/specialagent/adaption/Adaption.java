package io.opentracing.contrib.specialagent.adaption;

import java.util.Objects;

import com.grack.nanojson.JsonObject;

class Adaption {
  private final AdaptionType type;
  private final String key;
  Object value;

  Adaption(final JsonObject object, final String subject) {
    this.type = AdaptionType.parseType(Objects.requireNonNull(object, subject + ": Not an object"), subject);
    this.key = object.getString("key");
    this.value = object.get("value");
  }

  AdaptionType getType() {
    return this.type;
  }

  String getKey() {
    return this.key;
  }

  Object getValue() {
    return this.value;
  }
}