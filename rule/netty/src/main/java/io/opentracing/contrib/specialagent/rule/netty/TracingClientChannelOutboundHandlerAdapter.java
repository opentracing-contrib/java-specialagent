package io.opentracing.contrib.specialagent.rule.netty;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.HttpRequest;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.propagation.Format.Builtin;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;

// ClientRequest
public class TracingClientChannelOutboundHandlerAdapter extends ChannelOutboundHandlerAdapter {

  @Override
  public void write(final ChannelHandlerContext ctx, final Object msg, final ChannelPromise prm) {
    if (!(msg instanceof HttpRequest)) {
      ctx.write(msg, prm);
      return;
    }

    final HttpRequest request = (HttpRequest) msg;

    final Span span = GlobalTracer.get().buildSpan(request.method().name())
        .withTag(Tags.COMPONENT, "netty")
        .withTag(Tags.SPAN_KIND, Tags.SPAN_KIND_CLIENT)
        .withTag(Tags.HTTP_METHOD, request.method().name())
        .withTag(Tags.HTTP_URL, request.uri()).start();

    try (final Scope scope = GlobalTracer.get().activateSpan(span)) {
      // AWS calls are often signed, so we can't add headers without breaking the signature.
      if (!request.headers().contains("amz-sdk-invocation-id")) {
        GlobalTracer.get().inject(span.context(), Builtin.HTTP_HEADERS, new NettyInjectAdapter(request.headers()));
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
