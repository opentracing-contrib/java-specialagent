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
import io.opentracing.Tracer;
import io.opentracing.Tracer.SpanBuilder;
import io.opentracing.contrib.kafka.TracingKafkaUtils;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
import org.apache.kafka.clients.consumer.ConsumerRecord;

public class SpringKafkaAgentIntercept {
  private static final Logger log = Logger.getLogger(SpringKafkaAgentIntercept.class.getName());

  private static Boolean isKafkaVersionSupported;
  private static class Context {
    private int counter = 1;
    private Scope scope;
    private Span span;
  }

  private static final ThreadLocal<Context> contextHolder = new ThreadLocal<>();

  public static void onMessageEnter(Object record) {
    if (isKafkaVersionSupported == null) {
      try {
        Class.forName("org.apache.kafka.common.header.Headers");
        isKafkaVersionSupported = true;
      } catch (ClassNotFoundException e) {
        log.warning("Kafka prior version 1.0.0 is not supported");
        isKafkaVersionSupported = false;
      }
    }

    if (!isKafkaVersionSupported) {
      return;
    }

    if (contextHolder.get() != null) {
      ++contextHolder.get().counter;
      return;
    }

    final Context context = new Context();
    contextHolder.set(context);

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
    contextHolder.get().span = span;
    contextHolder.get().scope = tracer.activateSpan(span);
  }

  public static void onMessageExit(Throwable thrown) {
    final Context context = contextHolder.get();
    if (context != null) {
      --context.counter;
      if (context.counter == 0) {
        if (thrown != null) {
          captureException(context.span, thrown);
        }
        context.scope.close();
        context.span.finish();
        contextHolder.remove();
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