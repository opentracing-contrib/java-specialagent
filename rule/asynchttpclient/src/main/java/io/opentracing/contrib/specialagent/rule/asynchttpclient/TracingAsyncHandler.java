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

package io.opentracing.contrib.specialagent.rule.asynchttpclient;

import java.net.InetSocketAddress;
import java.util.List;

import javax.net.ssl.SSLSession;

import org.asynchttpclient.AsyncHandler;
import org.asynchttpclient.HttpResponseBodyPart;
import org.asynchttpclient.HttpResponseStatus;
import org.asynchttpclient.netty.request.NettyRequest;

import io.netty.channel.Channel;
import io.netty.handler.codec.http.HttpHeaders;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.contrib.specialagent.AgentRuleUtil;
import io.opentracing.tag.Tags;

public class TracingAsyncHandler implements AsyncHandler<Object> {
  private final Tracer tracer;
  private final AsyncHandler<?> handler;
  private final Span span;

  public TracingAsyncHandler(final Tracer tracer, final AsyncHandler<?> handler, final Span span) {
    this.tracer = tracer;
    this.handler = handler;
    this.span = span;
  }

  @Override
  public State onStatusReceived(final HttpResponseStatus responseStatus) throws Exception {
    span.setTag(Tags.HTTP_STATUS.getKey(), responseStatus.getStatusCode());
    return handler.onStatusReceived(responseStatus);
  }

  @Override
  public State onHeadersReceived(final HttpHeaders headers) throws Exception {
    return handler.onHeadersReceived(headers);
  }

  @Override
  public State onBodyPartReceived(final HttpResponseBodyPart bodyPart) throws Exception {
    return handler.onBodyPartReceived(bodyPart);
  }

  @Override
  public State onTrailingHeadersReceived(final HttpHeaders headers) throws Exception {
    return handler.onTrailingHeadersReceived(headers);
  }

  @Override
  @SuppressWarnings("deprecation")
  public void onThrowable(final Throwable t) {
    try (final Scope scope = tracer.scopeManager().activate(span, true)) {
      handler.onThrowable(t);
    }
    finally {
      AgentRuleUtil.setErrorTag(span, t);
    }
  }

  @Override
  @SuppressWarnings("deprecation")
  public Object onCompleted() throws Exception {
    try (final Scope scope = tracer.scopeManager().activate(span, true)) {
      return handler.onCompleted();
    }
  }

  @Override
  public void onHostnameResolutionAttempt(final String name) {
    handler.onHostnameResolutionAttempt(name);
  }

  @Override
  public void onHostnameResolutionSuccess(final String name, final List<InetSocketAddress> list) {
    handler.onHostnameResolutionSuccess(name, list);
  }

  @Override
  public void onHostnameResolutionFailure(final String name, final Throwable cause) {
    handler.onHostnameResolutionFailure(name, cause);
  }

  @Override
  public void onTcpConnectAttempt(final InetSocketAddress remoteAddress) {
    handler.onTcpConnectAttempt(remoteAddress);
  }

  @Override
  public void onTcpConnectSuccess(final InetSocketAddress remoteAddress, final Channel connection) {
    handler.onTcpConnectSuccess(remoteAddress, connection);
  }

  @Override
  public void onTcpConnectFailure(final InetSocketAddress remoteAddress, final Throwable cause) {
    handler.onTcpConnectFailure(remoteAddress, cause);
  }

  @Override
  public void onTlsHandshakeAttempt() {
    handler.onTlsHandshakeAttempt();
  }

  @Override
  public void onTlsHandshakeSuccess(final SSLSession sslSession) {
    handler.onTlsHandshakeSuccess(sslSession);
  }

  @Override
  public void onTlsHandshakeFailure(final Throwable cause) {
    handler.onTlsHandshakeFailure(cause);
  }

  @Override
  public void onConnectionPoolAttempt() {
    handler.onConnectionPoolAttempt();
  }

  @Override
  public void onConnectionPooled(final Channel connection) {
    handler.onConnectionPooled(connection);
  }

  @Override
  public void onConnectionOffer(final Channel connection) {
    handler.onConnectionOffer(connection);
  }

  @Override
  public void onRequestSend(final NettyRequest request) {
    handler.onRequestSend(request);
  }

  @Override
  public void onRetry() {
    handler.onRetry();
  }
}