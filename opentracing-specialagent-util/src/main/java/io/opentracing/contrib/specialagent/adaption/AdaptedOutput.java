package io.opentracing.contrib.specialagent.adaption;

class AdaptedOutput {
  final AdaptionType type;
  final String key;
  final Object value;

  AdaptedOutput(final AdaptionType type, final String key, final Object value) {
    this.type = type;
    this.key = key;
    this.value = value;
  }
}