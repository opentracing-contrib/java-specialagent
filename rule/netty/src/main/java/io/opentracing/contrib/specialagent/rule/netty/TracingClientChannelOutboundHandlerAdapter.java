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
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.HttpRequest;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.Tracer.SpanBuilder;
import io.opentracing.propagation.Format.Builtin;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;

// Client Request
public class TracingClientChannelOutboundHandlerAdapter extends ChannelOutboundHandlerAdapter {
  @Override
  public void write(final ChannelHandlerContext context, final Object message, final ChannelPromise promise) {
    if (!(message instanceof HttpRequest)) {
      context.write(message, promise);
      return;
    }

    final HttpRequest request = (HttpRequest)message;
    final Tracer tracer = GlobalTracer.get();
    final SpanBuilder builder = tracer
      .buildSpan(request.method().name())
      .withTag(Tags.COMPONENT, "netty")
      .withTag(Tags.SPAN_KIND, Tags.SPAN_KIND_CLIENT)
      .withTag(Tags.HTTP_METHOD, request.method().name())
      .withTag(Tags.HTTP_URL, request.uri());

    final SpanContext parentContext = tracer.extract(Builtin.HTTP_HEADERS, new NettyExtractAdapter(request.headers()));

    if (parentContext != null)
      builder.asChildOf(parentContext);

    final Span span = builder.start();
    try (final Scope scope = tracer.activateSpan(span)) {
      // AWS calls are often signed, so we can't add headers without breaking
      // the signature.
      if (!request.headers().contains("amz-sdk-invocation-id")) {
        tracer.inject(span.context(), Builtin.HTTP_HEADERS, new NettyInjectAdapter(request.headers()));
      }

      context.channel().attr(TracingClientChannelInboundHandlerAdapter.CLIENT_ATTRIBUTE_KEY).set(span);
      try {
        context.write(message, promise);
      }
      catch (final Throwable t) {
        TracingServerChannelInboundHandlerAdapter.onError(t, span);
        span.finish();
        throw t;
      }
    }
  }
}