package io.opentracing.contrib.specialagent.tracer;

abstract class SpanCustomizer {
  abstract void setTag(String key, Object value);
  abstract void addLogField(String key, Object value);
  abstract void setOperationName(String name);
}