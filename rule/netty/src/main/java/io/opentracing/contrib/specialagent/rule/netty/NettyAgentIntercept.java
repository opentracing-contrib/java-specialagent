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

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpRequestEncoder;
import io.netty.handler.codec.http.HttpResponseDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.handler.codec.http.HttpServerCodec;

public class NettyAgentIntercept {
  public static void pipelineAddExit(final Object thiz, final Object arg2) {
    final ChannelPipeline pipeline = (ChannelPipeline)thiz;
    final ChannelHandler handler = (ChannelHandler)arg2;

    try {
      // Server
      if (handler instanceof HttpServerCodec) {
        pipeline.addLast(TracingHttpServerHandler.class.getName(), new TracingHttpServerHandler());
      }
      else if (handler instanceof HttpRequestDecoder) {
        pipeline.addLast(TracingServerChannelInboundHandlerAdapter.class.getName(), new TracingServerChannelInboundHandlerAdapter());
      }
      else if (handler instanceof HttpResponseEncoder) {
        pipeline.addLast(TracingServerChannelOutboundHandlerAdapter.class.getName(), new TracingServerChannelOutboundHandlerAdapter());
      }
      else
      // Client
      if (handler instanceof HttpClientCodec) {
        pipeline.addLast(TracingHttpClientTracingHandler.class.getName(), new TracingHttpClientTracingHandler());
      }
      else if (handler instanceof HttpRequestEncoder) {
        pipeline.addLast(TracingClientChannelOutboundHandlerAdapter.class.getName(), new TracingClientChannelOutboundHandlerAdapter());
      }
      else if (handler instanceof HttpResponseDecoder) {
        pipeline.addLast(TracingClientChannelInboundHandlerAdapter.class.getName(), new TracingClientChannelInboundHandlerAdapter());
      }
    }
    catch (final IllegalArgumentException ignore) {
    }
  }
}