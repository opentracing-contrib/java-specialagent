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

package io.opentracing.contrib.specialagent.rule.pulsar.client;

import java.util.concurrent.CompletableFuture;

import org.apache.pulsar.client.api.Consumer;
import org.apache.pulsar.client.api.Message;
import org.apache.pulsar.client.api.MessageId;
import org.apache.pulsar.client.api.Producer;
import org.apache.pulsar.client.impl.MessageImpl;

import io.opentracing.References;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.Tracer.SpanBuilder;
import io.opentracing.contrib.specialagent.AgentRuleUtil;
import io.opentracing.contrib.specialagent.LocalSpanContext;
import io.opentracing.propagation.Format.Builtin;
import io.opentracing.propagation.TextMapAdapter;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;

public class PulsarClientAgentIntercept {
  static final String COMPONENT_NAME = "java-pulsar";

  private static void buildConsumerSpan(final Consumer<?> consumer, final Message<?> message) {
    final Tracer tracer = GlobalTracer.get();
    final SpanContext parentContext = tracer.extract(Builtin.TEXT_MAP, new TextMapAdapter(message.getProperties()));

    final SpanBuilder spanBuilder = tracer
      .buildSpan("receive")
      .withTag(Tags.COMPONENT, COMPONENT_NAME)
      .withTag(Tags.SPAN_KIND, Tags.SPAN_KIND_CONSUMER)
      .withTag("topic", consumer.getTopic())
      .withTag("subscription", consumer.getSubscription())
      .withTag(Tags.PEER_SERVICE, "pulsar");

    if (parentContext != null)
      spanBuilder.addReference(References.FOLLOWS_FROM, parentContext);

    spanBuilder.start().finish();
  }

  public static void receiveEnd(final Object thiz, final Object returned) {
    final Message<?> message = (Message<?>)returned;
    final Consumer<?> consumer = (Consumer<?>)thiz;
    buildConsumerSpan(consumer, message);
  }

  @SuppressWarnings("unchecked")
  public static void receiveAsyncEnd(final Object thiz, final Object returned) {
    final Consumer<?> consumer = (Consumer<?>)thiz;
    CompletableFuture<Message<?>> completableFuture = (CompletableFuture<Message<?>>)returned;
    completableFuture.thenAccept(message -> buildConsumerSpan(consumer, message));
  }

  public static void internalSendAsyncEnter(final Object thiz, final Object arg) {
    if (LocalSpanContext.get() != null) {
      LocalSpanContext.get().increment();
      return;
    }

    final MessageImpl<?> message = (MessageImpl<?>)arg;
    final Producer<?> producer = (Producer<?>)thiz;

    final Tracer tracer = GlobalTracer.get();
    final Span span = tracer
      .buildSpan("send")
      .withTag(Tags.COMPONENT, COMPONENT_NAME)
      .withTag(Tags.SPAN_KIND, Tags.SPAN_KIND_PRODUCER)
      .withTag(Tags.MESSAGE_BUS_DESTINATION, producer.getTopic())
      .withTag(Tags.PEER_SERVICE, "pulsar")
      .start();

    message.getProperties();

    tracer.inject(span.context(), Builtin.TEXT_MAP, new PropertiesMapInjectAdapter(message.getMessageBuilder()));

    final Scope scope = tracer.activateSpan(span);
    LocalSpanContext.set(span, scope);
  }

  @SuppressWarnings("unchecked")
  public static Object internalSendAsyncEnd(final Object returned, final Throwable thrown) {
    final LocalSpanContext context = LocalSpanContext.get();
    if (context == null)
      return returned;

    if (context.decrementAndGet() != 0)
      return returned;

    context.closeScope();
    final Span span = context.getSpan();

    if (thrown != null) {
      AgentRuleUtil.setErrorTag(span, thrown);
      span.finish();
      return returned;
    }

    return ((CompletableFuture<MessageId>)returned).thenApply(messageId -> {
      span.finish();
      return messageId;
    }).exceptionally(throwable -> {
      AgentRuleUtil.setErrorTag(span, throwable);
      span.finish();
      return null;
    });
  }
}