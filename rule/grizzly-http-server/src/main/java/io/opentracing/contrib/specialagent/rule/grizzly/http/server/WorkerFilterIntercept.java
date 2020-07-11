package io.opentracing.contrib.specialagent.rule.grizzly.http.server;

import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMap;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.http.HttpContent;
import org.glassfish.grizzly.http.HttpHeader;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.http.HttpResponsePacket;

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
