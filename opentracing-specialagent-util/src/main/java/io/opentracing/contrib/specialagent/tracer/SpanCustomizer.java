package io.opentracing.contrib.specialagent.tracer;

public interface SpanCustomizer {
  void setTag(String key, Object value);
  void addLogField(String key, Object value);
  void setOperationName(String name);
}