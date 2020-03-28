/* Copyright 2020 The OpenTracing Authors
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

package io.opentracing.contrib.specialagent.rule.kafka.streams;

import static org.awaitility.Awaitility.*;
import static org.hamcrest.core.IsEqual.*;
import static org.junit.Assert.*;

import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

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
import org.apache.kafka.streams.kstream.Predicate;
import org.apache.kafka.streams.kstream.Produced;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.kafka.test.rule.EmbeddedKafkaRule;
import org.springframework.kafka.test.utils.KafkaTestUtils;

import io.opentracing.contrib.specialagent.AgentRunner;
import io.opentracing.contrib.specialagent.TestUtil;
import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;

@RunWith(AgentRunner.class)
public class KafkaStreamsTest {
  @ClassRule
  public static final EmbeddedKafkaRule embeddedKafkaRule = new EmbeddedKafkaRule(2, true, 2, "stream-test");

  @Before
  public void before(final MockTracer tracer) {
    tracer.reset();
  }

  @Test
  public void test(final MockTracer tracer) {
    final Map<String,Object> senderProps = KafkaTestUtils.producerProps(embeddedKafkaRule.getEmbeddedKafka());
    try (final Producer<Integer,String> producer = new KafkaProducer<>(senderProps)) {
      final Properties config = new Properties();
      config.put(StreamsConfig.APPLICATION_ID_CONFIG, "stream-app");
      config.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, senderProps.get("bootstrap.servers"));
      config.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.Integer().getClass());
      config.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass());

      final ProducerRecord<Integer,String> record = new ProducerRecord<>("stream-test", 1, "test");
      producer.send(record);

      final Serde<String> stringSerde = Serdes.String();
      final Serde<Integer> intSerde = Serdes.Integer();

      final StreamsBuilder builder = new StreamsBuilder();
      final KStream<Integer,String> kStream = builder.stream("stream-test");

      kStream.map(new KeyValueMapper<Integer,String,KeyValue<Integer,String>>() {
        @Override
        public KeyValue<Integer,String> apply(final Integer key, final String value) {
          TestUtil.checkActiveSpan();
          return new KeyValue<>(key, value + "map");
        }
      }).filter(new Predicate<Integer,String>() {
        @Override
        public boolean test(final Integer key, final String value) {
          TestUtil.checkActiveSpan();
          return value.contains("map");
        }
      }).to("stream-out", Produced.with(intSerde, stringSerde));

      final KafkaStreams streams = new KafkaStreams(builder.build(), config);
      streams.start();
      await().atMost(15, TimeUnit.SECONDS).until(TestUtil.reportedSpansSize(tracer), equalTo(1));
      streams.close();
    }

    final List<MockSpan> mockSpans = tracer.finishedSpans();
    assertEquals(1, mockSpans.size());
    assertNull(tracer.activeSpan());
  }
}