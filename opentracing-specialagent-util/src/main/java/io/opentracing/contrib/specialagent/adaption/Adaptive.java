package io.opentracing.contrib.specialagent.adaption;

abstract class Adaptive extends Adapter {
  private boolean operationNameProcessed = false;

  Adaptive(final AdaptionRules rules) {
    super(rules);
  }

  final void processOperationName(final String operationName) {
    if (operationNameProcessed)
      throw new IllegalStateException();

    operationNameProcessed = true;
    if (!processRules(AdaptionRuleType.OPERATION_NAME, null, operationName))
      setOperationName(operationName);
  }

  final void processTag(final String key, final Object value) {
    if (!processRules(AdaptionRuleType.TAG, key, value))
      setTag(key, value);
  }
}