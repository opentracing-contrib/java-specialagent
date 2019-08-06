package io.opentracing.contrib.specialagent.proxy;

import java.util.Map.Entry;

import io.opentracing.SpanContext;

public class ProxySpanContext implements SpanContext {
  @Override
  public Iterable<Entry<String,String>> baggageItems() {
    return null;
  }

  @Override
  public String toTraceId() {
    return null;
  }

  @Override
  public String toSpanId() {
    return null;
  }
}