package io.opentracing.contrib.specialagent.adaption;

abstract class Adaptive extends Adapter {
  Adaptive(final AdaptionRules rules) {
    super(rules);
  }

  final void processServiceName(final String serviceName) {
    //cannot set the service name, only process it
    processRules(AdaptionRuleType.SERVICE_NAME, null, serviceName);
  }

  final void processOperationName(final String operationName) {
    if (!processRules(AdaptionRuleType.OPERATION_NAME, null, operationName))
      setOperationName(operationName);
  }

  final void processTag(final String key, final Object value) {
    if (!processRules(AdaptionRuleType.TAG, key, value))
      setTag(key, value);
  }
}
