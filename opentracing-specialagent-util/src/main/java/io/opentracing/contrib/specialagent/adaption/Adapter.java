package io.opentracing.contrib.specialagent.adaption;

abstract class Adapter {
  final AdaptionRules rules;

  Adapter(final AdaptionRules rules) {
    this.rules = rules;
  }

  abstract void adaptTag(String key, Object value);
  abstract void adaptLog(long timestampMicroseconds, String key, Object value);
  abstract void adaptOperationName(String name);

  final void processOperationName(final String operationName) {
    if (!processRules(AdaptionType.OPERATION_NAME, 0, null, operationName))
      adaptOperationName(operationName);
  }

  final void processStart() {
    // Cannot set the span start, only process it
    processRules(AdaptionType.START, 0, null, null);
  }

  final void processLog(final long timestampMicroseconds, final String key, final Object value) {
    if (!processRules(AdaptionType.LOG, timestampMicroseconds, key, value))
      adaptLog(timestampMicroseconds, key, value);
  }

  final void processTag(final String key, final Object value) {
    if (!processRules(AdaptionType.TAG, 0, key, value))
      adaptTag(key, value);
  }

  private boolean processRules(final AdaptionType type, final long timestampMicroseconds, final String key, final Object value) {
    for (final AdaptionRule rule : rules.getRules(key)) {
      if (rule.input.getType() != type)
        continue;

      final Object match = rule.match(value);
      if (match != null) {
        processMatch(rule, timestampMicroseconds, match, value);
        return true;
      }
    }

    return false;
  }

  final void processMatch(final AdaptionRule rule, final long timestampMicroseconds, final Object match, final Object input) {
    if (rule.outputs != null) {
      for (final Adaption adaptedOutput : rule.outputs) {
        final String key = adaptedOutput.getKey() != null ? adaptedOutput.getKey() : rule.input.getKey();
        final Object output = rule.adapt(match, input, adaptedOutput.getValue());
        adaptedOutput.getType().adapt(this, timestampMicroseconds, key, output);
      }
    }
  }
}