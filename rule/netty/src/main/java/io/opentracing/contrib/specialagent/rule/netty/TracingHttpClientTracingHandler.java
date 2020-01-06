package io.opentracing.contrib.specialagent.rule.netty;

import io.netty.channel.CombinedChannelDuplexHandler;

public class TracingHttpClientTracingHandler extends CombinedChannelDuplexHandler<TracingClientChannelInboundHandlerAdapter, TracingClientChannelOutboundHandlerAdapter> {

  public TracingHttpClientTracingHandler() {
    super(new TracingClientChannelInboundHandlerAdapter(), new TracingClientChannelOutboundHandlerAdapter());
  }
}
