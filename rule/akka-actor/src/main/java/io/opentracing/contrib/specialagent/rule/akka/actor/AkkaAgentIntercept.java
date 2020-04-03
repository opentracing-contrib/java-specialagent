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

package io.opentracing.contrib.specialagent.rule.akka.actor;

import java.util.HashMap;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.actor.DeadLetterActorRef;
import akka.pattern.PromiseActorRef;
import io.opentracing.References;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.Tracer.SpanBuilder;
import io.opentracing.contrib.specialagent.LocalSpanContext;
import io.opentracing.contrib.specialagent.OpenTracingApiUtil;
import io.opentracing.propagation.Format;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;

public class AkkaAgentIntercept {
  static final String COMPONENT_NAME = "java-akka";

  public static Object aroundReceiveStart(final Object thiz, final Object message) {
    if (!(message instanceof TracedMessage) && LocalSpanContext.get() != null) {
      LocalSpanContext.get().increment();
      return message;
    }

    final Tracer tracer = GlobalTracer.get();
    final SpanBuilder spanBuilder = tracer
      .buildSpan("receive")
      .withTag(Tags.COMPONENT, COMPONENT_NAME)
      .withTag(Tags.SPAN_KIND, Tags.SPAN_KIND_CONSUMER);

    final TracedMessage<?> tracedMessage;
    if (message instanceof TracedMessage) {
      tracedMessage = (TracedMessage<?>)message;
      spanBuilder.addReference(References.FOLLOWS_FROM, tracedMessage.spanContext(tracer));
    }
    else {
      tracedMessage = null;
      spanBuilder.withTag(Tags.MESSAGE_BUS_DESTINATION, ((AbstractActor)thiz).getSelf().path().toString());
    }

    final Span span = spanBuilder.start();
    final Scope scope = tracer.activateSpan(span);

    LocalSpanContext.set(span, scope);

    return tracedMessage != null ? tracedMessage.getMessage() : message;
  }

  public static void aroundReceiveEnd(final Throwable thrown) {
    final LocalSpanContext context = LocalSpanContext.get();
    if (context == null || context.decrementAndGet() != 0)
      return;

    if (thrown != null)
      OpenTracingApiUtil.setErrorTag(context.getSpan(), thrown);

    context.closeAndFinish();
  }

  @SuppressWarnings("deprecation")
  public static Object askStart(final Object arg0, final Object message, final String method, final Object sender) {
    if (arg0 instanceof DeadLetterActorRef)
      return message;

    if (arg0 instanceof ActorRef && ((ActorRef)arg0).isTerminated())
      return message;

    if (sender instanceof PromiseActorRef || arg0 instanceof PromiseActorRef)
      return message;

    if (sender instanceof ActorRef && ((ActorRef)sender).isTerminated())
      return message;

    final String path;
    if (arg0 instanceof ActorRef)
      path = ((ActorRef)arg0).path().toString();
    else if (arg0 instanceof ActorSelection)
      path = ((ActorSelection)arg0).toSerializationFormat();
    else
      return message;

    if (path.contains("/system/"))
      return message;

    final Tracer tracer = GlobalTracer.get();
    final Span span = tracer
      .buildSpan(method)
      .withTag(Tags.COMPONENT, COMPONENT_NAME)
      .withTag(Tags.SPAN_KIND, Tags.SPAN_KIND_PRODUCER)
      .withTag(Tags.MESSAGE_BUS_DESTINATION, path)
      .start();

    final HashMap<String,String> headers = new HashMap<>();
    tracer.inject(span.context(), Format.Builtin.TEXT_MAP_INJECT, headers::put);

    final Scope scope = tracer.activateSpan(span);
    LocalSpanContext.set(span, scope);

    return new TracedMessage<>(message, headers);
  }

  public static void askEnd(final Object arg0, final Object message, final Throwable thrown, final Object sender) {
    if (sender instanceof PromiseActorRef || arg0 instanceof PromiseActorRef || !(message instanceof TracedMessage))
      return;

    final LocalSpanContext context = LocalSpanContext.get();
    if (context == null)
      return;

    if (thrown != null)
      OpenTracingApiUtil.setErrorTag(context.getSpan(), thrown);

    context.closeAndFinish();
  }
}