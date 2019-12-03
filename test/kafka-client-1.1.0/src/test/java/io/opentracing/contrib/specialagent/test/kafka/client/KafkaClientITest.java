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

package io.opentracing.contrib.specialagent.test.kafka.client;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.springframework.kafka.test.rule.EmbeddedKafkaRule;
import org.springframework.kafka.test.utils.KafkaTestUtils;

import io.opentracing.contrib.specialagent.TestUtil;

public class KafkaClientITest {
  private static final int MESSAGE_COUNT = 5;
  private static final String TOPIC_NAME = "demo";

  public static void main(final String[] args) throws Exception {
    TestUtil.initTerminalExceptionHandler();
    final EmbeddedKafkaRule embeddedKafkaRule = TestUtil.retry(new Callable<EmbeddedKafkaRule>() {
      @Override
      public EmbeddedKafkaRule call() {
        final EmbeddedKafkaRule rule = new EmbeddedKafkaRule(1, true, 1, "messages");
        try {
          rule.before();
          return rule;
        }
        catch (final Exception e) {
          rule.after();
          throw e;
        }
      }
    }, 10);

    try (final Producer<Long,String> producer = createProducer(embeddedKafkaRule)) {
      for (int i = 0; i < MESSAGE_COUNT; ++i) {
        final ProducerRecord<Long,String> record = new ProducerRecord<>(TOPIC_NAME, "This is record " + i);
        producer.send(record, new Callback() {
          @Override
          public void onCompletion(final RecordMetadata metadata, final Exception exception) {
            TestUtil.checkActiveSpan();
          }
        });
      }

      final CountDownLatch latch = new CountDownLatch(1);
      createConsumer(embeddedKafkaRule, latch);
      latch.await(15, TimeUnit.SECONDS);
    }

    embeddedKafkaRule.after();
    TestUtil.checkSpan("java-kafka", 10);

    // Embedded Kafka and Zookeeper processes may not exit
    System.exit(0);
  }

  private static Producer<Long,String> createProducer(final EmbeddedKafkaRule embeddedKafkaRule) {
    final Map<String,Object> senderProps = KafkaTestUtils.producerProps(embeddedKafkaRule.getEmbeddedKafka());
    return new KafkaProducer<>(senderProps);
  }

  private static void createConsumer(final EmbeddedKafkaRule embeddedKafkaRule, final CountDownLatch latch) {
    Executors.newSingleThreadExecutor().execute(new Runnable() {
      @Override
      public void run() {
        final Map<String,Object> consumerProps = KafkaTestUtils.consumerProps("sampleRawConsumer", "false", embeddedKafkaRule.getEmbeddedKafka());
        consumerProps.put("auto.offset.reset", "earliest");
        try (final Consumer<Long,String> consumer = new KafkaConsumer<>(consumerProps)) {
          consumer.subscribe(Collections.singletonList(TOPIC_NAME));
          for (int i = 0; i < MESSAGE_COUNT;) {
            final int count = consumer.poll(100).count();
            for (int j = 0; j < count; ++j, ++i) {
              consumer.commitSync();
            }
          }
        }

        latch.countDown();
      }
    });
  }
}