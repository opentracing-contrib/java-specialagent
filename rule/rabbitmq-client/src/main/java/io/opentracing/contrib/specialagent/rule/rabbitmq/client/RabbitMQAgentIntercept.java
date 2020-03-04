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

package io.opentracing.contrib.specialagent.rule.rabbitmq.client;

import static io.opentracing.contrib.rabbitmq.TracingUtils.*;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.GetResponse;

import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.contrib.common.WrapperProxy;
import io.opentracing.contrib.rabbitmq.TracingConsumer;
import io.opentracing.contrib.rabbitmq.TracingUtils;
import io.opentracing.contrib.specialagent.AgentRuleUtil;
import io.opentracing.contrib.specialagent.LocalSpanContext;
import io.opentracing.util.GlobalTracer;

public class RabbitMQAgentIntercept {
  public static void exitGet(final Object response, final Object queue, final Throwable thrown) {
    final Span span = TracingUtils.buildChildSpan(((GetResponse)response).getProps(), (String)queue, GlobalTracer.get());
    if (thrown != null)
      AgentRuleUtil.setErrorTag(span, thrown);

    span.finish();
  }

  public static void finish(final Throwable thrown) {
    final LocalSpanContext context = LocalSpanContext.get();
    if (context == null)
      return;

    if (thrown != null)
      AgentRuleUtil.setErrorTag(context.getSpan(), thrown);

    context.closeAndFinish();
  }

  public static AMQP.BasicProperties enterPublish(final Object exchange, final Object routingKey, final Object props) {
    final AMQP.BasicProperties properties = (AMQP.BasicProperties)props;
    final Tracer tracer = GlobalTracer.get();
    final Span span = TracingUtils.buildSpan((String)exchange, (String)routingKey, properties, tracer);

    final Scope scope = tracer.activateSpan(span);
    LocalSpanContext.set(span, scope);

    return inject(properties, span, tracer);
  }

  public static Object enterConsume(final Object callback, final Object queue) {
    return WrapperProxy.wrap(callback, new TracingConsumer((Consumer)callback, (String)queue, GlobalTracer.get()));
  }

  public static void handleDeliveryStart(Object thiz, Object props) {
    if (WrapperProxy.isWrapper(thiz, TracingConsumer.class))
      return;

    if (AgentRuleUtil.callerEquals(1, 3, "io.opentracing.contrib.rabbitmq.TracingConsumer.handleDelivery"))
      return;

    final AMQP.BasicProperties properties = (AMQP.BasicProperties) props;
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