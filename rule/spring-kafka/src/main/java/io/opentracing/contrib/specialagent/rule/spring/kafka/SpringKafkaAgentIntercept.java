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

package io.opentracing.contrib.specialagent.rule.spring.kafka;

import io.opentracing.contrib.specialagent.LocalSpanContext;
import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.clients.consumer.ConsumerRecord;

import io.opentracing.References;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.Tracer.SpanBuilder;
import io.opentracing.contrib.kafka.TracingKafkaUtils;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;

public class SpringKafkaAgentIntercept {

  public static void onMessageEnter(final Object record) {
    if (LocalSpanContext.get() != null) {
      LocalSpanContext.get().increment();
      return;
    }

    final Tracer tracer = GlobalTracer.get();
    final SpanBuilder builder = tracer
      .buildSpan("onMessage")
      .withTag(Tags.COMPONENT, "spring-kafka")
      .withTag(Tags.SPAN_KIND, Tags.SPAN_KIND_CONSUMER);

    if (record instanceof ConsumerRecord) {
      final ConsumerRecord<?,?> consumerRecord = (ConsumerRecord<?,?>)record;
      final SpanContext spanContext = TracingKafkaUtils.extractSpanContext(consumerRecord.headers(), tracer);
      if (spanContext != null)
        builder.addReference(References.FOLLOWS_FROM, spanContext);
    }

    final Span span = builder.start();
    LocalSpanContext.set(span, tracer.activateSpan(span));
  }

  public static void onMessageExit(Throwable thrown) {
    final LocalSpanContext context = LocalSpanContext.get();
    if (context != null) {
      if (context.decrementAndGet() == 0) {
        if (thrown != null) {
          captureException(context.getSpan(), thrown);
        }
        context.closeAndFinish();
      }
    }
  }

  private static void captureException(final Span span, final Throwable t) {
    final Map<String,Object> exceptionLogs = new HashMap<>();
    exceptionLogs.put("event", Tags.ERROR.getKey());
    exceptionLogs.put("error.object", t);
    span.log(exceptionLogs);
    Tags.ERROR.set(span, true);
  }
}