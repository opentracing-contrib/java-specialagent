package io.opentracing.contrib.specialagent.proxy;

import io.opentracing.Scope;
import io.opentracing.ScopeManager;
import io.opentracing.Span;

public class ProxyScopeManager implements ScopeManager {
  final ScopeManager scopeManager;

  public ProxyScopeManager(final ScopeManager scopeManager) {
    this.scopeManager = scopeManager;
  }

  @Override
  public Scope activate(final Span span, final boolean finishSpanOnClose) {
    return null;
  }

  @Override
  public Scope active() {
    return null;
  }

  @Override
  public Scope activate(Span span) {
    return null;
  }

  @Override
  public Span activeSpan() {
    return null;
  }
}