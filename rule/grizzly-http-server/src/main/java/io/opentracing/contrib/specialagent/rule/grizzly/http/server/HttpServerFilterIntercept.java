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

public class HttpServerFilterIntercept {
    public static void onHandleReadExit(
            final FilterChainContext ctx,
            NextAction toReturn) {

        // See: org.glassfish.grizzly.filterchain.InvokeAction.TYPE
        // If we have have already started a span for this request
        // If we're not reading a request, ie. an http client
        if (toReturn.type() != 0 || !(ctx.getMessage() instanceof HttpContent) || SpanAssociations.get().hasSpanFor(ctx)) {
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
                .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_SERVER)
                .start();

//        Scope scope = tracer.scopeManager().activate(span);

        Tags.COMPONENT.set(span, "java-grizzly-http-server");
        Tags.HTTP_METHOD.set(span, request.getMethod().getMethodString());
        Tags.HTTP_URL.set(span, request.getRequestURI());

        ctx.addCompletionListener(new ScopeCompletionListener(span));

        SpanAssociations.get().associateSpan(ctx, span);
    }

    public static void onPrepareResponse(
            final FilterChainContext ctx,
            final HttpResponsePacket response) {
        Span toTag = SpanAssociations.get().retrieveSpan(ctx);
        if (toTag != null) {
            Tags.HTTP_STATUS.set(toTag, response.getStatus());
        }
    }

    public static class ScopeCompletionListener implements FilterChainContext.CompletionListener {
        private final Span span;
//        private final Scope scope;

        public ScopeCompletionListener(Span span) {
            this.span = span;
//            this.scope = scope;
        }

        @Override
        public void onComplete(FilterChainContext context) {
            span.finish();
//            scope.close();
            SpanAssociations.get().dispose(context);
        }
    }
}
