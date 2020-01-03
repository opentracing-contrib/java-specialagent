/* Copyright 2020 The OpenTracing Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentracing.contrib.specialagent.rule.netty;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.util.AttributeKey;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer.SpanBuilder;
import io.opentracing.propagation.Format.Builtin;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import java.util.HashMap;
import java.util.Map;

public class TracingServerChannelInboundHandlerAdapter extends ChannelInboundHandlerAdapter {
  public static final AttributeKey<Span> SERVER_ATTRIBUTE_KEY = AttributeKey.valueOf(
      TracingServerChannelInboundHandlerAdapter.class.getName() + ".span");

  @Override
  public void channelRead(final ChannelHandlerContext ctx, final Object msg) {

    if (!(msg instanceof HttpRequest)) {
      final Span span = ctx.channel().attr(SERVER_ATTRIBUTE_KEY).get();
      if (span == null) {
        ctx.fireChannelRead(msg);
      } else {
        try (final Scope scope = GlobalTracer.get().activateSpan(span)) {
          ctx.fireChannelRead(msg);
        }
      }
      return;
    }

    final HttpRequest request = (HttpRequest) msg;

    final SpanBuilder spanBuilder = GlobalTracer.get().buildSpan(request.getMethod().name())
        .withTag(Tags.COMPONENT, "netty")
        .withTag(Tags.SPAN_KIND, Tags.SPAN_KIND_SERVER)
        .withTag(Tags.HTTP_METHOD, request.method().name())
        .withTag(Tags.HTTP_URL, request.uri());

    final SpanContext context = GlobalTracer.get()
        .extract(Builtin.HTTP_HEADERS, new NettyExtractAdapter(request.headers()));
    if(context != null) {
      spanBuilder.asChildOf(context);
    }

    final Span span = spanBuilder.start();

    try (final Scope scope = GlobalTracer.get().activateSpan(span)) {
      ctx.channel().attr(SERVER_ATTRIBUTE_KEY).set(span);

      try {
        ctx.fireChannelRead(msg);
      } catch (final Throwable throwable) {
        onError(throwable, span);
        span.finish();
        throw throwable;
      }
    }
  }

  public static void onError(final Throwable t, final Span span) {
    Tags.ERROR.set(span, Boolean.TRUE);
    if (t != null)
      span.log(errorLogs(t));
  }

  private static Map<String,Object> errorLogs(final Throwable t) {
    final Map<String,Object> errorLogs = new HashMap<>(2);
    errorLogs.put("event", Tags.ERROR.getKey());
    errorLogs.put("error.object", t);
    return errorLogs;
  }
}
