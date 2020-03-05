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

package io.opentracing.contrib.specialagent.rule.spring.rabbitmq;

import java.util.Map;

import org.springframework.amqp.core.Message;

import com.rabbitmq.client.AMQP;

import io.opentracing.References;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.Tracer.SpanBuilder;
import io.opentracing.contrib.common.WrapperProxy;
import io.opentracing.contrib.rabbitmq.TracingConsumer;
import io.opentracing.contrib.rabbitmq.TracingUtils;
import io.opentracing.contrib.specialagent.AgentRuleUtil;
import io.opentracing.contrib.specialagent.LocalSpanContext;
import io.opentracing.propagation.Format.Builtin;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;

public class SpringRabbitMQAgentIntercept {
  public static void onMessageEnter(final Object msg) {
    if (LocalSpanContext.get() != null) {
      LocalSpanContext.get().increment();
      return;
    }

    final Tracer tracer = GlobalTracer.get();
    final SpanBuilder builder = tracer
      .buildSpan("onMessage")
      .withTag(Tags.COMPONENT, "spring-rabbitmq")
      .withTag(Tags.SPAN_KIND, Tags.SPAN_KIND_CONSUMER);

    final Message message = (Message)msg;
    if (message.getMessageProperties() != null) {
      final Map<String,Object> headers = message.getMessageProperties().getHeaders();
      final SpanContext spanContext = tracer.extract(Builtin.TEXT_MAP, new HeadersMapExtractAdapter(headers));
      if (spanContext != null)
        builder.addReference(References.FOLLOWS_FROM, spanContext);
    }

    final Span span = builder.start();
    LocalSpanContext.set(span, tracer.activateSpan(span));
  }

  public static void onMessageExit(final Throwable thrown) {
    final LocalSpanContext context = LocalSpanContext.get();
    if (context == null || context.decrementAndGet() != 0)
      return;

    if (thrown != null)
      AgentRuleUtil.setErrorTag(context.getSpan(), thrown);

    context.closeAndFinish();
  }

  public static void handleDeliveryStart(Object thiz, Object props) {
    if (WrapperProxy.isWrapper(thiz, TracingConsumer.class))
      return;

    if (AgentRuleUtil.callerEquals(1, 3, "io.opentracing.contrib.rabbitmq.TracingConsumer.handleDelivery"))
      return;

    final AMQP.BasicProperties properties = (AMQP.BasicProperties)props;
    final Tracer tracer = GlobalTracer.get();
    final Span span = TracingUtils.buildChildSpan(properties, null, tracer);
    final Scope scope = tracer.activateSpan(span);
    LocalSpanContext.set(span, scope);
  }

  public static void handleDeliveryEnd(final Throwable thrown) {
    final LocalSpanContext context = LocalSpanContext.get();
    if (context == null)
      return;

    if (thrown != null)
      AgentRuleUtil.setErrorTag(context.getSpan(), thrown);

    context.closeAndFinish();
  }
}