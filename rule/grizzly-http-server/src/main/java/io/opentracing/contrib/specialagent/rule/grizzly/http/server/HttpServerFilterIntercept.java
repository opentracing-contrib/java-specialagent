package io.opentracing.contrib.specialagent.rule.grizzly.http.server;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.contrib.grizzly.http.server.GizzlyHttpRequestPacketAdapter;
import io.opentracing.contrib.grizzly.http.server.GrizzlyServerSpanDecorator;
import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMap;
import io.opentracing.util.GlobalTracer;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.http.HttpContent;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.http.HttpResponsePacket;

public class HttpServerFilterIntercept {
    public static void onHandleReadExit(
            final Object ctxObj,
            Object toReturn) {

        FilterChainContext ctx = (FilterChainContext) ctxObj;

        // If not continuing to process
        // See: org.glassfish.grizzly.filterchain.InvokeAction.TYPE
        // If we have have already started a span for this request
        if (!(ctx.getMessage() instanceof HttpContent) || ((NextAction) toReturn).type() != 0 || SpanAssociations.get().hasSpanFor(ctx)) {
            return;
        }

        Tracer tracer = GlobalTracer.get();
        final HttpRequestPacket request = (HttpRequestPacket) ((HttpContent) ctx.getMessage()).getHttpHeader();

        TextMap adapter = new GizzlyHttpRequestPacketAdapter(request);
        SpanContext extractedContext = tracer.extract(Format.Builtin.HTTP_HEADERS,
                adapter);

        final Span span = tracer.buildSpan("HTTP::" + request.getMethod().getMethodString())
                .ignoreActiveSpan()
                .asChildOf(extractedContext)
                .start();

        GrizzlyServerSpanDecorator.STANDARD_TAGS.onRequest(request, span);
        ctx.addCompletionListener(new SpanCompletionListener(span));
        SpanAssociations.get().associateSpan(ctx, span);
    }

    public static void onPrepareResponse(
            final Object ctx,
            final Object response) {
        Span toTag = SpanAssociations.get().retrieveSpan(ctx);
        if (toTag != null) {
            GrizzlyServerSpanDecorator.STANDARD_TAGS.onResponse((HttpResponsePacket) response, toTag);
        }
    }

    public static class SpanCompletionListener implements FilterChainContext.CompletionListener {
        private final Span span;

        public SpanCompletionListener(Span span) {
            this.span = span;
        }

        @Override
        public void onComplete(FilterChainContext context) {
            span.finish();
            SpanAssociations.get().dispose(context);
        }
    }
}
