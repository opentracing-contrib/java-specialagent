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
import io.opentracing.Tracer;
import io.opentracing.Tracer.SpanBuilder;
import io.opentracing.contrib.specialagent.OpenTracingApiUtil;
import io.opentracing.propagation.Format.Builtin;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;

public class TracingServerChannelInboundHandlerAdapter extends ChannelInboundHandlerAdapter {
  public static final AttributeKey<Span> SERVER_ATTRIBUTE_KEY = AttributeKey.valueOf(TracingServerChannelInboundHandlerAdapter.class.getName() + ".span");

  @Override
  public void channelRead(final ChannelHandlerContext handlerContext, final Object message) {
    if (!(message instanceof HttpRequest)) {
      handlerContext.fireChannelRead(message);
      return;
    }

    final HttpRequest request = (HttpRequest)message;
    final Tracer tracer = GlobalTracer.get();

    final SpanBuilder spanBuilder = tracer.buildSpan(request.method().name())
      .withTag(Tags.COMPONENT, "netty")
      .withTag(Tags.SPAN_KIND, Tags.SPAN_KIND_SERVER)
      .withTag(Tags.HTTP_METHOD, request.method().name())
      .withTag(Tags.HTTP_URL, request.uri());

    final SpanContext spanContext = tracer.extract(Builtin.HTTP_HEADERS, new NettyExtractAdapter(request.headers()));
    if (spanContext != null)
      spanBuilder.asChildOf(spanContext);

    final Span span = spanBuilder.start();
    try (final Scope scope = tracer.activateSpan(span)) {
      handlerContext.channel().attr(SERVER_ATTRIBUTE_KEY).set(span);

      try {
        handlerContext.fireChannelRead(message);
      }
      catch (final Throwable t) {
        OpenTracingApiUtil.setErrorTag(span, t);
        span.finish();
        throw t;
      }
    }
  }
}