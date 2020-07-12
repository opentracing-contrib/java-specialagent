package io.opentracing.contrib.specialagent.rule.grizzly.http.server;

import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.util.GlobalTracer;

public class WorkerFilterIntercept {
    public static Scope onHandleReadEnter(
            final Object ctx) {
        Span span = SpanAssociations.get().retrieveSpan(ctx);
        if (span != null) {
            return GlobalTracer.get().scopeManager().activate(span);
        }

        return null;
    }

    public static void onHandleReadExit(Scope scope) {
        if (scope != null) {
            scope.close();
        }
    }
}
