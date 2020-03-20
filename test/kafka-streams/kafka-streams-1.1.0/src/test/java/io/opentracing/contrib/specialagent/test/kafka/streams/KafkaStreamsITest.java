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

package io.opentracing.contrib.specialagent.test.kafka.streams;


import io.opentracing.contrib.specialagent.TestUtil;
import io.opentracing.contrib.specialagent.TestUtil.ComponentSpanCount;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
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
import org.springframework.kafka.test.rule.EmbeddedKafkaRule;
import org.springframework.kafka.test.utils.KafkaTestUtils;

public class KafkaStreamsITest {
  public static void main(final String[] args) throws Exception {
    final EmbeddedKafkaRule embeddedKafkaRule = TestUtil.retry(new Callable<EmbeddedKafkaRule>() {
      @Override
      public EmbeddedKafkaRule call() {
        final EmbeddedKafkaRule rule = new EmbeddedKafkaRule(1, true, 1, "stream-test");
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

    final Map<String,Object> senderProps = KafkaTestUtils.producerProps(embeddedKafkaRule.getEmbeddedKafka());

    try (final Producer<Integer,String> producer = new KafkaProducer<>(senderProps)) {
      final CountDownLatch latch = TestUtil.initExpectedSpanLatch(4);

      Properties config = new Properties();
      config.put(StreamsConfig.APPLICATION_ID_CONFIG, "stream-app");
      config.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, senderProps.get("bootstrap.servers"));
      config.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.Integer().getClass());
      config.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass());

      ProducerRecord<Integer, String> record = new ProducerRecord<>("stream-test", 1, "test");
      producer.send(record);

      final Serde<String> stringSerde = Serdes.String();
      final Serde<Integer> intSerde = Serdes.Integer();

      StreamsBuilder builder = new StreamsBuilder();
      KStream<Integer, String> kStream = builder.stream("stream-test");

      kStream.map(new KeyValueMapper<Integer, String, KeyValue<Integer, String>>() {
        @Override
        public KeyValue<Integer, String> apply(Integer key, String value) {
          TestUtil.checkActiveSpan();
          return new KeyValue<>(key, value + "map");
        }
      }).to("stream-out", Produced.with(intSerde, stringSerde));

      KafkaStreams streams = new KafkaStreams(builder.build(), config);
      streams.start();

      TestUtil.checkSpan(true, latch, new ComponentSpanCount("java-kafka", 3), new ComponentSpanCount("kafka-streams", 1));
      streams.close();
    }
    catch (final Throwable t) {
      t.printStackTrace(System.err);
      embeddedKafkaRule.after();
      System.exit(1);
    }
    finally {
      embeddedKafkaRule.after();
      System.exit(0);
    }
  }

}