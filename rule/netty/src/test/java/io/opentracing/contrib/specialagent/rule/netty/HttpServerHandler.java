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

import static io.netty.handler.codec.http.HttpHeaders.Names.*;
import static io.netty.handler.codec.http.HttpResponseStatus.*;
import static io.netty.handler.codec.http.HttpVersion.*;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders.Values;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpUtil;

public class HttpServerHandler extends ChannelInboundHandlerAdapter {
  private static final byte[] CONTENT = { 'H', 'e', 'l', 'l', 'o', ' ', 'W', 'o', 'r', 'l', 'd' };

  @Override
  public void channelReadComplete(final ChannelHandlerContext handlerContext) {
    handlerContext.flush();
  }

  @Override
  public void channelRead(final ChannelHandlerContext handlerContext, final Object message) {
    if (message instanceof HttpRequest) {
      final HttpRequest request = (HttpRequest)message;
      if (HttpUtil.is100ContinueExpected(request))
        handlerContext.write(new DefaultFullHttpResponse(HTTP_1_1, CONTINUE));

      final boolean keepAlive = HttpUtil.isKeepAlive(request);
      final FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, OK, Unpooled.wrappedBuffer(CONTENT));
      response.headers().set(CONTENT_TYPE, "text/plain");
      response.headers().set(CONTENT_LENGTH, response.content().readableBytes());

      if (keepAlive) {
        response.headers().set(CONNECTION, Values.KEEP_ALIVE);
        handlerContext.write(response);
      }
      else {
        handlerContext.write(response).addListener(ChannelFutureListener.CLOSE);
      }
    }
  }

  @Override
  public void exceptionCaught(final ChannelHandlerContext handlerContext, final Throwable cause) {
    cause.printStackTrace();
    handlerContext.close();
  }
}