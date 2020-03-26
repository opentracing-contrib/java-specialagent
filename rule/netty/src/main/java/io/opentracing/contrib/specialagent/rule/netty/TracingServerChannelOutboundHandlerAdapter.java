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
import io.netty.handler.codec.http.HttpResponse;
import io.opentracing.Span;
import io.opentracing.contrib.specialagent.OpenTracingApiUtil;
import io.opentracing.tag.Tags;

public class TracingServerChannelOutboundHandlerAdapter extends ChannelOutboundHandlerAdapter {
  @Override
  public void write(final ChannelHandlerContext handlerContext, final Object message, final ChannelPromise promise) {
    final Span span = handlerContext.channel().attr(TracingServerChannelInboundHandlerAdapter.SERVER_ATTRIBUTE_KEY).get();
    if (span == null || !(message instanceof HttpResponse)) {
      handlerContext.write(message, promise);
      return;
    }

    final HttpResponse response = (HttpResponse)message;

    try {
      handlerContext.write(message, promise);
    }
    catch (final Throwable t) {
      OpenTracingApiUtil.setErrorTag(span, t);
      span.setTag(Tags.HTTP_STATUS, 500);
      span.finish(); // Finish the span manually since finishSpanOnClose was false
      throw t;
    }

    span.setTag(Tags.HTTP_STATUS, response.status().code());
    span.finish(); // Finish the span manually since finishSpanOnClose was false
  }
}