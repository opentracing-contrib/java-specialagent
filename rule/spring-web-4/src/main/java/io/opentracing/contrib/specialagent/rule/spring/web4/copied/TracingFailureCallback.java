package io.opentracing.contrib.specialagent.rule.spring.web4.copied;

import org.springframework.util.concurrent.FailureCallback;

import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.util.GlobalTracer;

public class TracingFailureCallback implements FailureCallback {
  private final FailureCallback callback;
  private final Span span;

  public TracingFailureCallback(FailureCallback callback, Span span) {
    this.callback = callback;
    this.span = span;
  }

  @Override
  public void onFailure(Throwable ex) {
    try(Scope scope = GlobalTracer.get().activateSpan(span)) {
      callback.onFailure(ex);
    }
  }
}
