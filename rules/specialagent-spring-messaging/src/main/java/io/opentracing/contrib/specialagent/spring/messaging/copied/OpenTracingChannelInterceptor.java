/**
 * Copyright 2017-2018 The OpenTracing Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.opentracing.contrib.specialagent.spring.messaging.copied;

import io.opentracing.References;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.Tracer.SpanBuilder;
import io.opentracing.propagation.Format;
import io.opentracing.tag.Tags;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.integration.channel.AbstractMessageChannel;
import org.springframework.integration.context.IntegrationObjectSupport;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.support.ChannelInterceptorAdapter;
import org.springframework.messaging.support.ExecutorChannelInterceptor;
import org.springframework.util.ClassUtils;

/**
 * @author <a href="mailto:gytis@redhat.com">Gytis Trikleris</a>
 */
public class OpenTracingChannelInterceptor extends ChannelInterceptorAdapter implements ExecutorChannelInterceptor {
  private static final Log log = LogFactory.getLog(OpenTracingChannelInterceptor.class);

  static final String COMPONENT_NAME = "spring-messaging";

  private final Tracer tracer;

  public OpenTracingChannelInterceptor(Tracer tracer) {
    this.tracer = tracer;
  }

  @Override
  public Message<?> preSend(Message<?> message, MessageChannel channel) {
    log.trace("Processing message before sending it to the channel");
    boolean isConsumer = message.getHeaders().containsKey(Headers.MESSAGE_SENT_FROM_CLIENT);

    SpanBuilder spanBuilder = tracer.buildSpan(getOperationName(channel, isConsumer))
        .withTag(Tags.SPAN_KIND.getKey(), isConsumer ? Tags.SPAN_KIND_CONSUMER : Tags.SPAN_KIND_PRODUCER)
        .withTag(Tags.COMPONENT.getKey(), COMPONENT_NAME)
        .withTag(Tags.MESSAGE_BUS_DESTINATION.getKey(), getChannelName(channel));

    MessageTextMap<?> carrier = new MessageTextMap<>(message);
    SpanContext extractedContext = tracer.extract(Format.Builtin.TEXT_MAP, carrier);
    if (isConsumer) {
      spanBuilder.addReference(References.FOLLOWS_FROM, extractedContext);
    } else if (tracer.activeSpan() == null) {
      // it's a client but active span is null
      // This is a fallback we try to add extractedContext in case there is something
      spanBuilder.asChildOf(extractedContext);
    }

    Span span = spanBuilder.startActive(true).span();

    if (isConsumer) {
      log.trace("Adding 'messageConsumed' header");
      carrier.put(Headers.MESSAGE_CONSUMED, "true");
      // TODO maybe we should remove Headers.MESSAGE_SENT_FROM_CLIENT header here?
    } else {
      log.trace("Adding 'messageSent' header");
      carrier.put(Headers.MESSAGE_SENT_FROM_CLIENT, "true");
    }

    tracer.inject(span.context(), Format.Builtin.TEXT_MAP, carrier);
    return carrier.getMessage();
  }

  @Override
  public void afterSendCompletion(Message<?> message, MessageChannel channel, boolean sent, Exception ex) {
    Scope scope = tracer.scopeManager().active();
    if (scope == null) {
      return;
    }

    log.trace(String.format("Completed sending and current span is %s", scope.span()));
    handleException(ex, scope.span());
    log.trace("Closing messaging span scope " + scope);
    scope.close();
    log.trace(String.format("Messaging span scope %s successfully closed", scope));
  }

  @Override
  public Message<?> beforeHandle(Message<?> message, MessageChannel channel, MessageHandler handler) {
    Span span = tracer.activeSpan();
    log.trace(String.format("Continuing span %s before handling message", span));

    return message;
  }

  @Override
  public void afterMessageHandled(Message<?> message, MessageChannel channel, MessageHandler handler, Exception ex) {
    Span span = tracer.activeSpan();
    log.trace(String.format("Continuing span %s after message handled", span));

    if (span == null) {
      return;
    }

    handleException(ex, span);
  }

  /**
   * Add exception related tags and logs to a span
   *
   * @param ex   exception or null
   * @param span span
   */
  protected void handleException(Exception ex, Span span) {
    if (ex != null) {
      Tags.ERROR.set(span, true);
      // TODO add exception logs
    }
  }

  protected String getChannelName(MessageChannel messageChannel) {
    String name = null;
    if (ClassUtils.isPresent("org.springframework.integration.context.IntegrationObjectSupport", null)) {
      if (messageChannel instanceof IntegrationObjectSupport) {
        name = ((IntegrationObjectSupport) messageChannel).getComponentName();
      }
      if (name == null && messageChannel instanceof AbstractMessageChannel) {
        name = ((AbstractMessageChannel) messageChannel).getFullChannelName();
      }
    }

    if (name == null) {
      return messageChannel.toString();
    }

    return name;
  }

  protected String getOperationName(MessageChannel messageChannel, boolean isConsumer) {
    String channelName = getChannelName(messageChannel);
    return String.format("%s:%s", isConsumer ? "receive" : "send", channelName);
  }
}
