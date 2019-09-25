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

package io.opentracing.contrib.specialagent.akka;

import java.util.HashMap;
import java.util.Map;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.actor.DeadLetterActorRef;
import akka.pattern.PromiseActorRef;
import io.opentracing.References;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.Tracer.SpanBuilder;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;

public class AkkaAgentIntercept {
  private static final ThreadLocal<Context> contextHolder = new ThreadLocal<>();
  static final String COMPONENT_NAME = "java-akka";

  private static class Context {
    private Scope scope;
    private Span span;
    private int counter = 1;
  }

  public static Object aroundReceiveStart(final Object thiz, final Object message) {
    if (!(message instanceof TracedMessage) && contextHolder.get() != null) {
      ++contextHolder.get().counter;
      return message;
    }

    final AbstractActor actor = (AbstractActor)thiz;

    final SpanBuilder spanBuilder = GlobalTracer.get()
      .buildSpan("receive")
      .withTag(Tags.COMPONENT, COMPONENT_NAME)
      .withTag(Tags.SPAN_KIND, Tags.SPAN_KIND_CONSUMER);

    final TracedMessage<?> tracedMessage;
    if (message instanceof TracedMessage) {
      tracedMessage = (TracedMessage<?>)message;
      spanBuilder.addReference(References.FOLLOWS_FROM, tracedMessage.span.context());
    }
    else {
      tracedMessage = null;
      spanBuilder.withTag(Tags.MESSAGE_BUS_DESTINATION, actor.getSelf().path().toString());
    }

    final Span span = spanBuilder.start();
    final Scope scope = GlobalTracer.get().activateSpan(span);

    final Context context = new Context();
    contextHolder.set(context);
    context.scope = scope;
    context.span = span;

    return tracedMessage != null ? tracedMessage.message : message;
  }

  public static void aroundReceiveEnd(final Throwable thrown) {
    final Context context = contextHolder.get();
    if (context == null)
      return;

    if (--context.counter != 0)
      return;

    if (thrown != null)
      onError(thrown, context.span);

    context.scope.close();
    context.span.finish();
    contextHolder.remove();
  }

  public static Object askStart(final Object arg0, final Object message, final String method, final Object sender) {
    if (arg0 instanceof DeadLetterActorRef)
      return message;

    if (arg0 instanceof ActorRef && ((ActorRef)arg0).isTerminated())
      return message;

    if (sender instanceof PromiseActorRef || arg0 instanceof PromiseActorRef)
      return message;

    if (sender instanceof ActorRef && ((ActorRef)sender).isTerminated())
      return message;

    final Span span = GlobalTracer.get()
      .buildSpan(method)
      .withTag(Tags.COMPONENT, COMPONENT_NAME)
      .withTag(Tags.SPAN_KIND, Tags.SPAN_KIND_PRODUCER).start();

    if (arg0 instanceof ActorRef)
      span.setTag(Tags.MESSAGE_BUS_DESTINATION, ((ActorRef)arg0).path().toString());
    else if (arg0 instanceof ActorSelection)
      span.setTag(Tags.MESSAGE_BUS_DESTINATION, ((ActorSelection)arg0).toSerializationFormat());

    return new TracedMessage<>(message, span, GlobalTracer.get().activateSpan(span));
  }

  public static void askEnd(Object arg0, Object message, Throwable thrown, Object sender) {
    if (sender instanceof PromiseActorRef || arg0 instanceof PromiseActorRef)
      return;

    if (!(message instanceof TracedMessage))
      return;

    final TracedMessage<?> tracedMessage = (TracedMessage<?>)message;
    final Span span = tracedMessage.span;
    if (span == null)
      return;

    if (thrown != null)
      onError(thrown, span);

    tracedMessage.closeScopeAndSpan();
  }

  private static void onError(final Throwable t, final Span span) {
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