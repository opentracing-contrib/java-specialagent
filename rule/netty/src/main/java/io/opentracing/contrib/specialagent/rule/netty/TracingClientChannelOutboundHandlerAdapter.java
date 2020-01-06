package io.opentracing.contrib.specialagent.rule.netty;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.util.AttributeKey;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer.SpanBuilder;
import io.opentracing.propagation.Format.Builtin;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;

// ClientRequest
public class TracingClientChannelOutboundHandlerAdapter extends ChannelOutboundHandlerAdapter {
  public static final AttributeKey<Span> CLIENT_PARENT_ATTRIBUTE_KEY = AttributeKey.valueOf(TracingClientChannelOutboundHandlerAdapter.class.getName() + ".parent");

  @Override
  public void write(final ChannelHandlerContext ctx, final Object msg, final ChannelPromise prm) {
    if (!(msg instanceof HttpRequest)) {
      ctx.write(msg, prm);
      return;
    }

    final HttpRequest request = (HttpRequest) msg;

    final SpanBuilder builder = GlobalTracer.get().buildSpan(request.method().name())
        .withTag(Tags.COMPONENT, "netty")
        .withTag(Tags.SPAN_KIND, Tags.SPAN_KIND_CLIENT)
        .withTag(Tags.HTTP_METHOD, request.method().name())
        .withTag(Tags.HTTP_URL, request.uri());

    final Span parentSpan = ctx.channel().attr(CLIENT_PARENT_ATTRIBUTE_KEY).getAndRemove();

    SpanContext parentContext;
    if (parentSpan != null) {
      parentContext = parentSpan.context();
    } else {
      parentContext = GlobalTracer.get().extract(Builtin.HTTP_HEADERS, new NettyExtractAdapter(request.headers()));
    }

    if (parentContext != null) {
      builder.asChildOf(parentContext);
    }

    final Span span = builder.start();

    try (final Scope scope = GlobalTracer.get().activateSpan(span)) {
      // AWS calls are often signed, so we can't add headers without breaking the signature.
      if (!request.headers().contains("amz-sdk-invocation-id")) {
        GlobalTracer.get().inject(span.context(), Builtin.HTTP_HEADERS,
            new NettyInjectAdapter(request.headers()));
      }

      ctx.channel().attr(TracingClientChannelInboundHandlerAdapter.CLIENT_ATTRIBUTE_KEY).set(span);

      try {
        ctx.write(msg, prm);
      } catch (final Throwable throwable) {
        TracingServerChannelInboundHandlerAdapter.onError(throwable, span);
        span.finish();
        throw throwable;
      }
    }

  }

}
