package io.opentracing.contrib.specialagent.spring.web.copied;

import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.util.GlobalTracer;
import org.springframework.util.concurrent.SuccessCallback;

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
