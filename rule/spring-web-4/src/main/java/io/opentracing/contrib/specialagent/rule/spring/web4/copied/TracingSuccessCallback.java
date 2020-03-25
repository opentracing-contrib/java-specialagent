package io.opentracing.contrib.specialagent.rule.spring.web4.copied;

import org.springframework.util.concurrent.SuccessCallback;

import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.util.GlobalTracer;

public class TracingSuccessCallback implements SuccessCallback {
  private final SuccessCallback callback;
  private final Span span;

  public TracingSuccessCallback(SuccessCallback callback, Span span) {
    this.callback = callback;
    this.span = span;
  }

  @Override
  public void onSuccess(Object result) {
    try(Scope scope = GlobalTracer.get().activateSpan(span)) {
      callback.onSuccess(result);
    }
  }

}
