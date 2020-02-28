package io.opentracing.contrib.specialagent.adaption;

abstract class Adaptive extends Adapter {
  Adaptive(final AdaptionRules rules) {
    super(rules);
  }

  final void processOperationName(final String operationName) {
    if (!processRules(AdaptionType.OPERATION_NAME, 0, null, operationName))
      adaptOperationName(operationName);
  }

  final void processServiceName(final String serviceName) {
    // Cannot set the service name, only process it
    if (serviceName != null)
      processRules(AdaptionType.SERVICE_NAME, 0, null, serviceName);
  }

  final void processTag(final String key, final Object value) {
    if (!processRules(AdaptionType.TAG, 0, key, value))
      adaptTag(key, value);
  }
}