package io.opentracing.contrib.specialagent.adaption;

abstract class Adaptive extends Adapter {
  Adaptive(final AdaptionRules rules) {
    super(rules);
  }

  final void processOperationName(final String operationName) {
    if (!processRules(AdaptionType.OPERATION_NAME, 0, null, operationName))
      adaptOperationName(operationName);
  }

  final void processSpanStart() {
    // Cannot set the span start, only process it
    processRules(AdaptionType.SPAN, 0, null, null);
  }

  final void processTag(final String key, final Object value) {
    if (!processRules(AdaptionType.TAG, 0, key, value))
      adaptTag(key, value);
  }
}
