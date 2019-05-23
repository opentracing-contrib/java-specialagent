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

package io.opentracing.contrib.specialagent.rabbitmq;

import static io.opentracing.contrib.rabbitmq.TracingUtils.*;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.GetResponse;

import io.opentracing.Span;
import io.opentracing.contrib.rabbitmq.TracingConsumer;
import io.opentracing.contrib.rabbitmq.TracingUtils;
import io.opentracing.util.GlobalTracer;

public class RabbitMQAgentIntercept {
  public static void exitGet(final Object response) {
    TracingUtils.buildAndFinishChildSpan(((GetResponse)response).getProps(), GlobalTracer.get());
  }

  public static void exitPublish() {
    GlobalTracer.get().scopeManager().active().close();
  }

  public static BasicProperties enterPublish(final Object exchange, final Object props) {
    final AMQP.BasicProperties properties = (BasicProperties)props;
    final Span span = TracingUtils.buildSpan((String)exchange, properties, GlobalTracer.get());
    GlobalTracer.get().scopeManager().activate(span, true);
    return inject(properties, span, GlobalTracer.get());
  }

  public static Object enterConsume(final Object callback) {
    return new TracingConsumer((Consumer)callback, GlobalTracer.get());
  }
}