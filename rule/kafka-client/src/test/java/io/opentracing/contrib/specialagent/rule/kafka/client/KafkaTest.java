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

package io.opentracing.contrib.specialagent.rule.kafka.client;

import static org.awaitility.Awaitility.*;
import static org.hamcrest.core.IsEqual.*;
import static org.junit.Assert.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KeyValueMapper;
import org.apache.kafka.streams.kstream.Produced;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.kafka.test.rule.EmbeddedKafkaRule;
import org.springframework.kafka.test.utils.KafkaTestUtils;

import io.opentracing.SpanContext;
import io.opentracing.contrib.kafka.TracingKafkaUtils;
import io.opentracing.contrib.specialagent.AgentRunner;
import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;

@RunWith(AgentRunner.class)
@AgentRunner.Config(isolateClassLoader = false)
public class KafkaTest {
  @ClassRule
  public static final EmbeddedKafkaRule embeddedKafkaRule = new EmbeddedKafkaRule(2, true, 2, "messages");

  @Before
  public void before(final MockTracer tracer) {
    tracer.reset();
  }

  @Test
  public void clients(final MockTracer tracer) throws Exception {
    try (final Producer<Integer,String> producer = createProducer()) {
      // Send 1
      producer.send(new ProducerRecord<>("messages", 1, "test"));

      // Send 2
      producer.send(new ProducerRecord<>("messages", 1, "test"));

      final CountDownLatch latch = new CountDownLatch(2);
      createConsumer(latch, 1, tracer);
    }

    final List<MockSpan> mockSpans = tracer.finishedSpans();
    assertEquals(4, mockSpans.size());
    assertNull(tracer.activeSpan());
  }

  @Test
  public void streams(final MockTracer tracer) {
    try (final Producer<Integer,String> producer = createProducer()) {
      final ProducerRecord<Integer,String> record = new ProducerRecord<>("stream-test", 1, "test");
      producer.send(record);
    }

    final Serde<String> stringSerde = Serdes.String();
    final Serde<Integer> intSerde = Serdes.Integer();

    final StreamsBuilder builder = new StreamsBuilder();
    final KStream<Integer,String> kStream = builder.stream("stream-test");
    kStream.map(new KeyValueMapper<Integer,String,KeyValue<Integer,String>>() {
      @Override
      public KeyValue<Integer,String> apply(final Integer key, final String value) {
        return new KeyValue<>(key, value + "map");
      }
    }).to("stream-out", Produced.with(intSerde, stringSerde));

    final Map<String,Object> senderProps = KafkaTestUtils.producerProps(embeddedKafkaRule.getEmbeddedKafka());

    final Properties config = new Properties();
    config.put(StreamsConfig.APPLICATION_ID_CONFIG, "stream-app");
    config.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, senderProps.get("bootstrap.servers"));
    config.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.Integer().getClass());
    config.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass());

    final KafkaStreams streams = new KafkaStreams(builder.build(), config);
    streams.start();

    await().atMost(15, TimeUnit.SECONDS).until(() -> tracer.finishedSpans().size(), equalTo(3));
    streams.close();

    final List<MockSpan> finishedSpans = tracer.finishedSpans();
    assertEquals(3, finishedSpans.size());

    assertNull(tracer.activeSpan());
  }

  private static Producer<Integer,String> createProducer() {
    final Map<String,Object> senderProps = KafkaTestUtils.producerProps(embeddedKafkaRule.getEmbeddedKafka());
    return new KafkaProducer<>(senderProps);
  }

  private static void createConsumer(final CountDownLatch latch, final Integer key, final MockTracer tracer) throws Exception {
    final ExecutorService executorService = Executors.newSingleThreadExecutor();

    final Map<String,Object> consumerProps = KafkaTestUtils.consumerProps("sampleRawConsumer", "false", embeddedKafkaRule.getEmbeddedKafka());
    consumerProps.put("auto.offset.reset", "earliest");

    executorService.execute(() -> {
      try (final KafkaConsumer<Integer,String> consumer = new KafkaConsumer<>(consumerProps)) {
        consumer.subscribe(Collections.singletonList("messages"));
        while (latch.getCount() > 0) {
          final ConsumerRecords<Integer,String> records = consumer.poll(100);
          for (final ConsumerRecord<Integer,String> record : records) {
            final SpanContext spanContext = TracingKafkaUtils.extractSpanContext(record.headers(), tracer);
            assertNotNull(spanContext);
            assertEquals("test", record.value());
            if (key != null)
              assertEquals(key, record.key());

            consumer.commitSync();
            latch.countDown();
          }
        }
      }
    });

    assertTrue(latch.await(30, TimeUnit.SECONDS));
  }
}