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

package io.opentracing.contrib.specialagent.rule.spring.websocket;

import java.util.List;
import java.util.Map;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.support.ExecutorChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.NativeMessageHeaderAccessor;
import org.springframework.web.socket.messaging.SubProtocolWebSocketHandler;
import org.springframework.web.socket.messaging.WebSocketAnnotationMethodMessageHandler;

import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.Tracer.SpanBuilder;
import io.opentracing.propagation.Format;
import io.opentracing.propagation.Format.Builtin;
import io.opentracing.tag.Tags;

/**
 * This class implements a {@link ExecutorChannelInterceptor} to instrument the websocket
 * communications using an OpenTracing Tracer.
 */
public class TracingChannelInterceptor implements  ExecutorChannelInterceptor {
  /** The span component tag value. */
  protected static final String WEBSOCKET = "websocket";

  /** The STOMP simple destination. */
  protected static final String SIMP_DESTINATION = "simpDestination";

  /** The STOMP simple message type, values defined in enum {@link SimpMessageType}. */
  protected static final String SIMP_MESSAGE_TYPE = "simpMessageType";

  /** Indicates that the destination is unknown. */
  private static final String UNKNOWN_DESTINATION = "Unknown";

  /** Header name used to carry the current {@link Span} from the initial preSend
   * phase to the beforeHandle phase. */
  public static final String OPENTRACING_SPAN = "opentracing.span";

  /** Header name used to carry the current active scope. */
  protected static final String OPENTRACING_SCOPE = "opentracing.scope";

  private final Tracer tracer;
  private final String spanKind;

  public TracingChannelInterceptor(final Tracer tracer, final String spanKind) {
    this.tracer = tracer;
    this.spanKind = spanKind;
  }

  @Override
  public Message<?> preSend(final Message<?> message, final MessageChannel channel) {
    if (SimpMessageType.MESSAGE.equals(message.getHeaders().get(SIMP_MESSAGE_TYPE))) {
      if (Tags.SPAN_KIND_SERVER.equals(spanKind))
        return preSendServerSpan(message);

      if (Tags.SPAN_KIND_CLIENT.equals(spanKind))
        return preSendClientSpan(message);
    }

    return message;
  }

  private Message<?> preSendClientSpan(final Message<?> message) {
    final String destination = (String)message.getHeaders().get(SIMP_DESTINATION);
    final Span span = tracer
      .buildSpan(destination != null ? destination : UNKNOWN_DESTINATION)
      .withTag(Tags.SPAN_KIND.getKey(), spanKind)
      .withTag(Tags.COMPONENT.getKey(), WEBSOCKET).start();
    final MessageBuilder<?> messageBuilder = MessageBuilder.fromMessage(message).setHeader(OPENTRACING_SPAN, span);
    tracer.inject(span.context(), Format.Builtin.TEXT_MAP, new TextMapInjectAdapter(messageBuilder));
    return messageBuilder.build();
  }

  @SuppressWarnings("unchecked")
  private Message<?> preSendServerSpan(Message<?> message) {
    final String destination = (String)message.getHeaders().get(SIMP_DESTINATION);
    final SpanBuilder spanBuilder = tracer
      .buildSpan(destination != null ? destination : UNKNOWN_DESTINATION)
      .withTag(Tags.SPAN_KIND.getKey(), spanKind)
      .withTag(Tags.COMPONENT.getKey(), WEBSOCKET);

    final Map<String,List<String>> nativeHeaders = (Map<String,List<String>>)message.getHeaders().get(NativeMessageHeaderAccessor.NATIVE_HEADERS);
    SpanContext spanContext = null;
    if (nativeHeaders != null)
      spanContext = tracer.extract(Builtin.TEXT_MAP, new NativeHeadersExtractAdapter(nativeHeaders));

    if (spanContext == null)
      spanContext = tracer.extract(Format.Builtin.TEXT_MAP, new TextMapExtractAdapter(message.getHeaders()));

    if (spanContext != null)
      spanBuilder.asChildOf(spanContext);

    final Span span = spanBuilder.start();
    return MessageBuilder.fromMessage(message).setHeader(OPENTRACING_SPAN, span).build();
  }

  @Override
  public void afterMessageHandled(final Message<?> message, final MessageChannel channel, final MessageHandler handler, final Exception arg3) {
    if ((handler instanceof WebSocketAnnotationMethodMessageHandler || handler instanceof SubProtocolWebSocketHandler) && SimpMessageType.MESSAGE.equals(message.getHeaders().get(SIMP_MESSAGE_TYPE))) {
      message.getHeaders().get(OPENTRACING_SCOPE, Scope.class).close();
      message.getHeaders().get(OPENTRACING_SPAN, Span.class).finish();
      // MessageHeaders are immutable
      // message.getHeaders().remove(OPENTRACING_SCOPE);
    }
  }

  @Override
  public Message<?> beforeHandle(final Message<?> message, final MessageChannel channel, final MessageHandler handler) {
    if ((!(handler instanceof WebSocketAnnotationMethodMessageHandler) && !(handler instanceof SubProtocolWebSocketHandler)) || !SimpMessageType.MESSAGE.equals(message.getHeaders().get(SIMP_MESSAGE_TYPE)))
      return message;

    final Span span = message.getHeaders().get(OPENTRACING_SPAN, Span.class);
    final Scope scope = tracer.scopeManager().activate(span);
    return MessageBuilder.fromMessage(message).setHeader(OPENTRACING_SCOPE, scope).build();
  }

}
