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

import static io.opentracing.contrib.rabbitmq.TracingUtils.inject;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.GetResponse;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.contrib.rabbitmq.TracingConsumer;
import io.opentracing.contrib.rabbitmq.TracingUtils;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import java.util.HashMap;
import java.util.Map;

public class RabbitMQAgentIntercept {
  private static final ThreadLocal<Context> contextHolder = new ThreadLocal<>();

  private static class Context {
    private Scope scope;
    private Span span;
  }

  public static void exitGet(final Object response, final Object queue, final Throwable thrown) {
    final Span span = TracingUtils.buildChildSpan(((GetResponse) response).getProps(), (String) queue, GlobalTracer.get());
    if(thrown != null) {
      captureException(span, thrown);
    }
    span.finish();
  }

  public static void exitPublish(Throwable thrown) {
    finish(thrown);
  }

  public static BasicProperties enterPublish(final Object exchange, final Object routingKey,
      final Object props) {
    final AMQP.BasicProperties properties = (BasicProperties) props;
    final Span span = TracingUtils
        .buildSpan((String) exchange, (String) routingKey, properties, GlobalTracer.get());

    final Scope scope = GlobalTracer.get().activateSpan(span);
    final Context context = new Context();
    contextHolder.set(context);
    context.scope = scope;
    context.span = span;

    return inject(properties, span, GlobalTracer.get());
  }

  public static Object enterConsume(final Object callback, final Object queue) {
    return new TracingConsumer((Consumer) callback, (String) queue, GlobalTracer.get());
  }

  private static void finish(Throwable thrown) {
    final Context context = contextHolder.get();
    if (context != null) {
      if (thrown != null) {
        captureException(context.span, thrown);
      }
      context.scope.close();
      context.span.finish();
      contextHolder.remove();
    }
  }

  private static void captureException(final Span span, final Throwable thrown) {
    final Map<String, Object> exceptionLogs = new HashMap<>();
    exceptionLogs.put("event", Tags.ERROR.getKey());
    exceptionLogs.put("error.object", thrown);
    span.log(exceptionLogs);
    Tags.ERROR.set(span, true);
  }
}