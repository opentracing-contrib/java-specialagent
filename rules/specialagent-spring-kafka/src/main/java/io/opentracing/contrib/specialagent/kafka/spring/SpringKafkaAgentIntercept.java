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

package io.opentracing.contrib.specialagent.kafka.spring;

import io.opentracing.References;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer.SpanBuilder;
import io.opentracing.contrib.kafka.TracingKafkaUtils;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import org.apache.kafka.clients.consumer.ConsumerRecord;

public class SpringKafkaAgentIntercept {
  private static class Context {
    private int counter = 1;
    private Scope scope;
    private Span span;
  }

  private static final ThreadLocal<Context> contextHolder = new ThreadLocal<>();

  public static void onMessageEnter(Object record) {
    if (contextHolder.get() != null) {
      ++contextHolder.get().counter;
      return;
    }

    final Context context = new Context();
    contextHolder.set(context);

    final SpanBuilder builder = GlobalTracer.get()
        .buildSpan("onMessage")
        .withTag(Tags.COMPONENT, "spring-kafka")
        .withTag(Tags.SPAN_KIND, Tags.SPAN_KIND_CONSUMER);

    if (record instanceof ConsumerRecord) {
      ConsumerRecord consumerRecord = (ConsumerRecord) record;
      final SpanContext spanContext = TracingKafkaUtils
          .extractSpanContext(consumerRecord.headers(), GlobalTracer.get());
      if (spanContext != null) {
        builder.addReference(References.FOLLOWS_FROM, spanContext);
      }
    }

    final Span span = builder.start();
    contextHolder.get().span = span;
    contextHolder.get().scope = GlobalTracer.get().activateSpan(span);

  }

  public static void onMessageExit() {
    final Context context = contextHolder.get();
    if (context != null) {
      --context.counter;
      if (context.counter == 0) {
        context.scope.close();
        context.span.finish();
        contextHolder.remove();
      }
    }
  }
}