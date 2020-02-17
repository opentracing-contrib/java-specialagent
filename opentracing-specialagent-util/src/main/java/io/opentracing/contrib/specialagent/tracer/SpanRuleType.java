package io.opentracing.contrib.specialagent.tracer;

public enum SpanRuleType {
  log {
    @Override
    void apply(SpanCustomizer customizer, String key, Object value) {
      customizer.addLogField(key, value);
    }
  },
  operationName {
    @Override
    void apply(SpanCustomizer customizer, String key, Object value) {
      customizer.setOperationName(value.toString());
    }
  },
  tag {
    @Override
    void apply(SpanCustomizer customizer, String key, Object value) {
      customizer.setTag(key, value);
    }
  };

  abstract void apply(SpanCustomizer customizer, String key, Object value);
}
