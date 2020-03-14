/* Copyright 2019 The OpenTracing Authors
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

package io.opentracing.contrib.specialagent.rule.playws;

import java.net.InetSocketAddress;
import java.util.List;

import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.contrib.specialagent.OpenTracingApiUtil;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import play.shaded.ahc.io.netty.channel.Channel;
import play.shaded.ahc.io.netty.handler.codec.http.HttpHeaders;
import play.shaded.ahc.org.asynchttpclient.AsyncHandler;
import play.shaded.ahc.org.asynchttpclient.HttpResponseBodyPart;
import play.shaded.ahc.org.asynchttpclient.HttpResponseStatus;
import play.shaded.ahc.org.asynchttpclient.Response;
import play.shaded.ahc.org.asynchttpclient.netty.request.NettyRequest;

public class TracingAsyncHandler implements AsyncHandler<Object> {
  private final Response.ResponseBuilder builder = new Response.ResponseBuilder();
  private final AsyncHandler<?> asyncHandler;
  private final Span span;

  public TracingAsyncHandler(final AsyncHandler<?> asyncHandler, final Span span) {
    this.asyncHandler = asyncHandler;
    this.span = span;
  }

  @Override
  public State onStatusReceived(final HttpResponseStatus httpResponseStatus) throws Exception {
    builder.reset();
    builder.accumulate(httpResponseStatus);
    return asyncHandler.onStatusReceived(httpResponseStatus);
  }

  @Override
  public State onHeadersReceived(final HttpHeaders httpHeaders) throws Exception {
    builder.accumulate(httpHeaders);
    return asyncHandler.onHeadersReceived(httpHeaders);
  }

  @Override
  public State onBodyPartReceived(final HttpResponseBodyPart httpResponseBodyPart) throws Exception {
    builder.accumulate(httpResponseBodyPart);
    return asyncHandler.onBodyPartReceived(httpResponseBodyPart);
  }

  @Override
  public State onTrailingHeadersReceived(final HttpHeaders headers) throws Exception {
    return asyncHandler.onTrailingHeadersReceived(headers);
  }

  @Override
  public void onThrowable(final Throwable throwable) {
    OpenTracingApiUtil.setErrorTag(span, throwable);
    try {
      asyncHandler.onThrowable(throwable);
    }
    finally {
      span.finish();
    }
  }

  @Override
  public Object onCompleted() throws Exception {
    final Response response = builder.build();
    if (response != null)
      span.setTag(Tags.HTTP_STATUS, response.getStatusCode());

    try (final Scope scope = GlobalTracer.get().activateSpan(span)) {
      return asyncHandler.onCompleted();
    }
    finally {
      span.finish();
    }
  }

  @Override
  public void onHostnameResolutionAttempt(final String name) {
    asyncHandler.onHostnameResolutionAttempt(name);
  }

  @Override
  public void onHostnameResolutionSuccess(final String name, final List<InetSocketAddress> list) {
    asyncHandler.onHostnameResolutionSuccess(name, list);
  }

  @Override
  public void onHostnameResolutionFailure(final String name, final Throwable cause) {
    asyncHandler.onHostnameResolutionFailure(name, cause);
  }

  @Override
  public void onTcpConnectAttempt(final InetSocketAddress remoteAddress) {
    asyncHandler.onTcpConnectAttempt(remoteAddress);
  }

  @Override
  public void onTcpConnectSuccess(final InetSocketAddress remoteAddress, final Channel connection) {
    asyncHandler.onTcpConnectSuccess(remoteAddress, connection);
  }

  @Override
  public void onTcpConnectFailure(final InetSocketAddress remoteAddress, final Throwable cause) {
    asyncHandler.onTcpConnectFailure(remoteAddress, cause);
  }

  @Override
  public void onTlsHandshakeAttempt() {
    asyncHandler.onTlsHandshakeAttempt();
  }

  @Override
  public void onTlsHandshakeSuccess() {
    asyncHandler.onTlsHandshakeSuccess();
  }

  @Override
  public void onTlsHandshakeFailure(final Throwable cause) {
    asyncHandler.onTlsHandshakeFailure(cause);
  }

  @Override
  public void onConnectionPoolAttempt() {
    asyncHandler.onConnectionPoolAttempt();
  }

  @Override
  public void onConnectionPooled(final Channel connection) {
    asyncHandler.onConnectionPooled(connection);
  }

  @Override
  public void onConnectionOffer(final Channel connection) {
    asyncHandler.onConnectionOffer(connection);
  }

  @Override
  public void onRequestSend(final NettyRequest request) {
    asyncHandler.onRequestSend(request);
  }

  @Override
  public void onRetry() {
    asyncHandler.onRetry();
  }
}