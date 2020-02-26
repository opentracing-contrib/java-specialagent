package io.opentracing.contrib.specialagent.adaption;

abstract class Adaptive extends Adapter {
  Adaptive(final AdaptionRules rules) {
    super(rules);
  }

  final void processOperationName(final String operationName) {
    if (!processRules(AdaptionRuleType.OPERATION_NAME, null, operationName))
      adaptOperationName(operationName);
  }

  final void processTag(final String key, final Object value) {
    if (!processRules(AdaptionRuleType.TAG, key, value))
      adaptTag(key, value);
  }
}