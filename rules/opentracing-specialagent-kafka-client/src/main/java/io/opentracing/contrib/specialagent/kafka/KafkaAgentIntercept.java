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
package io.opentracing.contrib.specialagent.kafka;

import io.opentracing.Scope;
import io.opentracing.contrib.kafka.TracingCallback;
import io.opentracing.contrib.kafka.TracingKafkaUtils;
import io.opentracing.util.GlobalTracer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.ProducerRecord;

public class KafkaAgentIntercept {

  public static Object producerCallback(Object record, Object callback) {
    Scope scope = TracingKafkaUtils
        .buildAndInjectSpan((ProducerRecord) record, GlobalTracer.get());

    return new TracingCallback((Callback) callback, scope.span(), GlobalTracer.get());
  }

  public static void onProducerExit() {
    final Scope active = GlobalTracer.get().scopeManager().active();
    if (active != null) {
      active.close();
    }
  }

  public static void consumerRecords(Object records) {
    for (Object record : (ConsumerRecords) records) {
      TracingKafkaUtils.buildAndFinishChildSpan((ConsumerRecord) record, GlobalTracer.get());
    }
  }
}