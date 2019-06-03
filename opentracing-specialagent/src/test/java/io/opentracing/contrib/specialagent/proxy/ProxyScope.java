package io.opentracing.contrib.specialagent.proxy;

import io.opentracing.Scope;
import io.opentracing.Span;

public class ProxyScope implements Scope {
  @Override
  public void close() {
  }

  @Override
  public Span span() {
    return null;
  }
}