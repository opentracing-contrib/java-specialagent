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

import io.opentracing.contrib.specialagent.TestUtil;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.springframework.kafka.test.rule.EmbeddedKafkaRule;
import org.springframework.kafka.test.utils.KafkaTestUtils;

public class KafkaClientITest {
  private static final Integer MESSAGE_COUNT = 5;
  private static final String TOPIC_NAME = "demo";

  public static void main(final String[] args) throws InterruptedException {
    TestUtil.initTerminalExceptionHandler();
    final EmbeddedKafkaRule embeddedKafkaRule = new EmbeddedKafkaRule(1, true, 1, "messages");
    embeddedKafkaRule.before();

    final Producer<Long, String> producer = createProducer(embeddedKafkaRule);

    for (int index = 0; index < MESSAGE_COUNT; index++) {
      ProducerRecord<Long, String> record = new ProducerRecord<>(TOPIC_NAME, "This is record " + index);
      producer.send(record, new Callback() {
        @Override
        public void onCompletion(RecordMetadata metadata, Exception exception) {
          TestUtil.checkActiveSpan();
        }
      });
    }

    CountDownLatch latch = new CountDownLatch(1);
    createConsumer(embeddedKafkaRule, latch);
    producer.close();
    latch.await(15, TimeUnit.SECONDS);
    embeddedKafkaRule.after();

    TestUtil.checkSpan("java-kafka", 10);
  }

  private static Producer<Long, String> createProducer(
      EmbeddedKafkaRule embeddedKafkaRule) {
    final Map<String, Object> senderProps = KafkaTestUtils
        .producerProps(embeddedKafkaRule.getEmbeddedKafka());
    return new KafkaProducer<>(senderProps);
  }

  private static void createConsumer(EmbeddedKafkaRule embeddedKafkaRule, CountDownLatch latch) {
    ExecutorService executorService = Executors.newSingleThreadExecutor();
    executorService.execute(() -> {
      final Map<String, Object> consumerProps = KafkaTestUtils
          .consumerProps("sampleRawConsumer", "false", embeddedKafkaRule.getEmbeddedKafka());
      consumerProps.put("auto.offset.reset", "earliest");
      Consumer<Long, String> consumer = new KafkaConsumer<>(consumerProps);
      consumer.subscribe(Collections.singletonList(TOPIC_NAME));

      int count = 0;
      while (count < MESSAGE_COUNT) {
        ConsumerRecords<Long, String> records = consumer.poll(100);
        for (ConsumerRecord<Long, String> record : records) {
          consumer.commitSync();
          count++;
        }
      }
      consumer.close();
      latch.countDown();
    });
  }
}