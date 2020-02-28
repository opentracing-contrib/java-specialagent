package io.opentracing.contrib.specialagent.adaption;

class AdaptedOutput {
  final AdaptionType type;
  final String key;
  final Object value;

  public AdaptedOutput(final AdaptionType type, final String key, final Object value) {
    this.type = type;
    this.key = key;
    this.value = value;
  }
}