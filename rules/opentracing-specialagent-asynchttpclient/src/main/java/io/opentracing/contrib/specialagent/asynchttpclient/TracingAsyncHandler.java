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
package io.opentracing.contrib.specialagent.asynchttpclient;

import io.netty.channel.Channel;
import io.netty.handler.codec.http.HttpHeaders;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.net.ssl.SSLSession;
import org.asynchttpclient.AsyncHandler;
import org.asynchttpclient.HttpResponseBodyPart;
import org.asynchttpclient.HttpResponseStatus;
import org.asynchttpclient.netty.request.NettyRequest;

public class TracingAsyncHandler implements AsyncHandler {
  private final AsyncHandler handler;
  private final Span span;
  private final Tracer tracer;

  public TracingAsyncHandler(AsyncHandler handler, Span span) {
    this.handler = handler;
    this.span = span;
    tracer = GlobalTracer.get();
  }

  @Override
  public State onStatusReceived(HttpResponseStatus responseStatus) throws Exception {
    span.setTag(Tags.HTTP_STATUS.getKey(), responseStatus.getStatusCode());
    return handler.onStatusReceived(responseStatus);
  }

  @Override
  public State onHeadersReceived(HttpHeaders headers) throws Exception {
    return handler.onHeadersReceived(headers);
  }

  @Override
  public State onBodyPartReceived(HttpResponseBodyPart bodyPart) throws Exception {
    return handler.onBodyPartReceived(bodyPart);
  }

  @Override
  public State onTrailingHeadersReceived(HttpHeaders headers) throws Exception {
    return handler.onTrailingHeadersReceived(headers);
  }

  @Override
  public void onThrowable(Throwable t) {
    try(Scope scope = tracer.scopeManager().activate(span, true)) {
      handler.onThrowable(t);
    } finally {
      onError(t, span);
    }
  }

  @Override
  public Object onCompleted() throws Exception {
    try(Scope scope = tracer.scopeManager().activate(span, true)) {
      return handler.onCompleted();
    }
  }

  @Override
  public void onHostnameResolutionAttempt(String name) {
    handler.onHostnameResolutionAttempt(name);
  }

  @Override
  public void onHostnameResolutionSuccess(String name, List list) {
    handler.onHostnameResolutionSuccess(name, list);
  }

  @Override
  public void onHostnameResolutionFailure(String name, Throwable cause) {
    handler.onHostnameResolutionFailure(name, cause);
  }

  @Override
  public void onTcpConnectAttempt(InetSocketAddress remoteAddress) {
    handler.onTcpConnectAttempt(remoteAddress);
  }

  @Override
  public void onTcpConnectSuccess(InetSocketAddress remoteAddress,
      Channel connection) {
    handler.onTcpConnectSuccess(remoteAddress, connection);
  }

  @Override
  public void onTcpConnectFailure(InetSocketAddress remoteAddress, Throwable cause) {
    handler.onTcpConnectFailure(remoteAddress, cause);
  }

  @Override
  public void onTlsHandshakeAttempt() {
    handler.onTlsHandshakeAttempt();
  }

  @Override
  public void onTlsHandshakeSuccess(SSLSession sslSession) {
    handler.onTlsHandshakeSuccess(sslSession);
  }

  @Override
  public void onTlsHandshakeFailure(Throwable cause) {
    handler.onTlsHandshakeFailure(cause);
  }

  @Override
  public void onConnectionPoolAttempt() {
    handler.onConnectionPoolAttempt();
  }

  @Override
  public void onConnectionPooled(Channel connection) {
    handler.onConnectionPooled(connection);
  }

  @Override
  public void onConnectionOffer(Channel connection) {
    handler.onConnectionOffer(connection);
  }

  @Override
  public void onRequestSend(NettyRequest request) {
    handler.onRequestSend(request);
  }

  @Override
  public void onRetry() {
    handler.onRetry();
  }

  private static void onError(Throwable throwable, Span span) {
    Tags.ERROR.set(span, Boolean.TRUE);

    if (throwable != null) {
      span.log(errorLogs(throwable));
    }
  }

  private static Map<String, Object> errorLogs(Throwable throwable) {
    Map<String, Object> errorLogs = new HashMap<>(2);
    errorLogs.put("event", Tags.ERROR.getKey());
    errorLogs.put("error.object", throwable);
    return errorLogs;
  }
}
