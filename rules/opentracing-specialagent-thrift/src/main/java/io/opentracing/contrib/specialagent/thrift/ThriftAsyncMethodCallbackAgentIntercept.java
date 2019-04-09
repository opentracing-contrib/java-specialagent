package io.opentracing.contrib.specialagent.thrift;

import io.opentracing.Scope;
import io.opentracing.thrift.DefaultClientSpanDecorator;
import io.opentracing.util.GlobalTracer;

public class ThriftAsyncMethodCallbackAgentIntercept {
  public static void onComplete() {
    Scope scope = GlobalTracer.get().scopeManager().active();
    if (scope != null) {
      scope.close();
    }
  }

  public static void onError(Object exception) {
    Scope scope = GlobalTracer.get().scopeManager().active();
    if (scope != null) {
      new DefaultClientSpanDecorator().onError((Throwable) exception, scope.span());
      scope.close();
    }
  }
}
