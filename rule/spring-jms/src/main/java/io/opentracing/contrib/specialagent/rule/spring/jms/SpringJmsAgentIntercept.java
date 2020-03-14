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

package io.opentracing.contrib.specialagent.rule.spring.jms;

import javax.jms.Message;

import io.opentracing.References;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.Tracer.SpanBuilder;
import io.opentracing.contrib.common.WrapperProxy;
import io.opentracing.contrib.jms.common.SpanContextContainer;
import io.opentracing.contrib.jms.common.TracingMessageConsumer;
import io.opentracing.contrib.jms.common.TracingMessageUtils;
import io.opentracing.contrib.specialagent.LocalSpanContext;
import io.opentracing.contrib.specialagent.OpenTracingApiUtil;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;

public class SpringJmsAgentIntercept {
  public static void onMessageEnter(final Object msg) {
    if (LocalSpanContext.get() != null) {
      LocalSpanContext.get().increment();
      return;
    }

    final Tracer tracer = GlobalTracer.get();
    final SpanBuilder builder = tracer
      .buildSpan("onMessage")
      .withTag(Tags.COMPONENT, "spring-jms")
      .withTag(Tags.SPAN_KIND, Tags.SPAN_KIND_CONSUMER);

    SpanContext spanContext = null;
    if (msg instanceof SpanContextContainer)
      spanContext = ((SpanContextContainer)msg).getSpanContext();

    if (spanContext == null)
      spanContext = TracingMessageUtils.extract((Message)msg, tracer);

    if (spanContext != null)
      builder.addReference(References.FOLLOWS_FROM, spanContext);

    final Span span = builder.start();
    LocalSpanContext.set(span, tracer.activateSpan(span));
  }

  public static void onMessageExit(final Throwable thrown) {
    final LocalSpanContext context = LocalSpanContext.get();
    if (context == null || context.decrementAndGet() != 0)
      return;

    if (thrown != null)
      OpenTracingApiUtil.setErrorTag(context.getSpan(), thrown);

    context.closeAndFinish();
  }

  public static void onReceiveMessage(final Object consumer, final Object message) {
    if (!WrapperProxy.isWrapper(consumer, TracingMessageConsumer.class))
      TracingMessageUtils.buildAndFinishChildSpan((Message)message, GlobalTracer.get());
  }
}