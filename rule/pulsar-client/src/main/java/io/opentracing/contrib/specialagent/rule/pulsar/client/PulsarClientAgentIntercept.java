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

import io.opentracing.References;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.Tracer.SpanBuilder;
import io.opentracing.propagation.Format.Builtin;
import io.opentracing.propagation.TextMapAdapter;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.apache.pulsar.client.api.Consumer;
import org.apache.pulsar.client.api.Message;
import org.apache.pulsar.client.api.MessageId;
import org.apache.pulsar.client.api.Producer;
import org.apache.pulsar.client.impl.MessageImpl;

public class PulsarClientAgentIntercept {
  private static final ThreadLocal<Context> contextHolder = new ThreadLocal<>();

  private static class Context {
    private Scope scope;
    private Span span;
    private int counter = 1;
  }

  private static void buildConsumerSpan(Consumer<?> consumer, Message<?> message) {
    final Tracer tracer = GlobalTracer.get();
    final SpanContext parentContext = tracer
        .extract(Builtin.TEXT_MAP, new TextMapAdapter(message.getProperties()));

    final SpanBuilder spanBuilder = tracer.buildSpan("receive")
        .withTag(Tags.COMPONENT, "pulsar")
        .withTag(Tags.SPAN_KIND, Tags.SPAN_KIND_CONSUMER)
        .withTag("topic", consumer.getTopic())
        .withTag("subscription", consumer.getSubscription())
        .withTag(Tags.PEER_SERVICE, "pulsar");

    if(parentContext != null) {
      spanBuilder.addReference(References.FOLLOWS_FROM, parentContext);
    }

    spanBuilder.start().finish();
  }

  public static void receiveEnd(Object thiz, Object returned) {
    Message<?> message = (Message<?>) returned;
    Consumer<?> consumer = (Consumer<?>) thiz;
    buildConsumerSpan(consumer, message);
  }

  @SuppressWarnings("unchecked")
  public static void receiveAsyncEnd(Object thiz, Object returned) {
    final Consumer<?> consumer = (Consumer<?>) thiz;
    CompletableFuture<Message<?>> completableFuture = (CompletableFuture<Message<?>>) returned;
    completableFuture.thenAccept(message -> {
      buildConsumerSpan(consumer, message);
    });
  }

  public static Object internalSendAsyncEnter(Object thiz, Object arg) {
    if (contextHolder.get() != null) {
      ++contextHolder.get().counter;
      return arg;
    }

    MessageImpl<?> message = (MessageImpl<?>) arg;
    Producer<?> producer = (Producer<?>) thiz;

    final Tracer tracer = GlobalTracer.get();

    final Span span = tracer.buildSpan("send")
        .withTag(Tags.COMPONENT, "pulsar")
        .withTag(Tags.SPAN_KIND, Tags.SPAN_KIND_PRODUCER)
        .withTag(Tags.MESSAGE_BUS_DESTINATION, producer.getTopic())
        .withTag(Tags.PEER_SERVICE, "pulsar")
        .start();

    message.getProperties();

    tracer.inject(span.context(), Builtin.TEXT_MAP, new PropertiesMapInjectAdapter(message.getMessageBuilder()));

    final Scope scope = GlobalTracer.get().activateSpan(span);

    final Context context = new Context();
    contextHolder.set(context);
    context.scope = scope;
    context.span = span;

    return arg;
  }

  @SuppressWarnings("unchecked")
  public static Object internalSendAsyncEnd(Object returned, Throwable thrown) {
    final Context context = contextHolder.get();
    if (context == null)
      return returned;

    if (--context.counter != 0)
      return returned;

    context.scope.close();

    if (thrown != null) {
      onError(thrown, context.span);
      context.span.finish();
      return returned;
    }

    final Span span = context.span;
    contextHolder.remove();

    return ((CompletableFuture<MessageId>) returned)
        .thenApply(messageId -> {
          span.finish();
          return messageId;
        }).exceptionally(throwable -> {
          onError(throwable, span);
          span.finish();
          return null;
        });
  }

  static void onError(final Throwable t, final Span span) {
    Tags.ERROR.set(span, Boolean.TRUE);
    if (t != null)
      span.log(errorLogs(t));
  }

  private static Map<String,Object> errorLogs(final Throwable t) {
    final Map<String,Object> errorLogs = new HashMap<>(2);
    errorLogs.put("event", Tags.ERROR.getKey());
    errorLogs.put("error.object", t);
    return errorLogs;
  }
}