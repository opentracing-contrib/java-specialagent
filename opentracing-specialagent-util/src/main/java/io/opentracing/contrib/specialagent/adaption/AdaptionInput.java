package io.opentracing.contrib.specialagent.adaption;

import java.util.regex.Pattern;

import com.grack.nanojson.JsonObject;

class AdaptionInput extends Adaption {
  AdaptionInput(final JsonObject object, final String subject) {
    super(object, subject + ".input");
    if (value instanceof String)
      value = Pattern.compile((String)value);
  }
}