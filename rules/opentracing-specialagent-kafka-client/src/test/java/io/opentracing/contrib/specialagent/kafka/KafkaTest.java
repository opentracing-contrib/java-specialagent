package io.opentracing.contrib.specialagent.kafka;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import io.opentracing.SpanContext;
import io.opentracing.contrib.kafka.TracingKafkaUtils;
import io.opentracing.contrib.specialagent.AgentRunner;
import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Callable;
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

@RunWith(AgentRunner.class)
@AgentRunner.Config(isolateClassLoader = false)
public class KafkaTest {
  @ClassRule
  public static EmbeddedKafkaRule embeddedKafkaRule = new EmbeddedKafkaRule(2, true, 2, "messages");

  @Before
  public void before(final MockTracer tracer) {
    tracer.reset();
  }

  @Test
  public void clients(MockTracer tracer) throws Exception {
    Producer<Integer, String> producer = createProducer();

    // Send 1
    producer.send(new ProducerRecord<>("messages", 1, "test"));

    // Send 2
    producer.send(new ProducerRecord<>("messages", 1, "test"));

    final CountDownLatch latch = new CountDownLatch(2);
    createConsumer(latch, 1, tracer);

    producer.close();

    List<MockSpan> mockSpans = tracer.finishedSpans();
    assertEquals(4, mockSpans.size());
    assertNull(tracer.activeSpan());
  }

  @Test
  public void streams(MockTracer tracer) {
    Map<String, Object> senderProps = KafkaTestUtils
        .producerProps(embeddedKafkaRule.getEmbeddedKafka());

    Properties config = new Properties();
    config.put(StreamsConfig.APPLICATION_ID_CONFIG, "stream-app");
    config.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, senderProps.get("bootstrap.servers"));
    config.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.Integer().getClass());
    config.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass());

    Producer<Integer, String> producer = createProducer();
    ProducerRecord<Integer, String> record = new ProducerRecord<>("stream-test", 1, "test");
    producer.send(record);

    final Serde<String> stringSerde = Serdes.String();
    final Serde<Integer> intSerde = Serdes.Integer();

    StreamsBuilder builder = new StreamsBuilder();
    KStream<Integer, String> kStream = builder.stream("stream-test");

    kStream.map(new KeyValueMapper<Integer, String, KeyValue<Integer, String>>() {
      @Override
      public KeyValue<Integer, String> apply(Integer key, String value) {
        return new KeyValue<>(key, value + "map");
      }
    }).to("stream-out", Produced.with(intSerde, stringSerde));

    KafkaStreams streams = new KafkaStreams(builder.build(), config);
    streams.start();

    await().atMost(15, TimeUnit.SECONDS).until(reportedSpansSize(tracer), equalTo(3));

    streams.close();
    producer.close();

    List<MockSpan> spans = tracer.finishedSpans();
    assertEquals(3, spans.size());

    assertNull(tracer.activeSpan());
  }


  private Producer<Integer, String> createProducer() {
    Map<String, Object> senderProps = KafkaTestUtils
        .producerProps(embeddedKafkaRule.getEmbeddedKafka());
    return new KafkaProducer<>(senderProps);
  }


  private void createConsumer(final CountDownLatch latch, final Integer key,
      final MockTracer tracer) throws Exception {

    ExecutorService executorService = Executors.newSingleThreadExecutor();

    final Map<String, Object> consumerProps = KafkaTestUtils
        .consumerProps("sampleRawConsumer", "false", embeddedKafkaRule.getEmbeddedKafka());
    consumerProps.put("auto.offset.reset", "earliest");

    executorService.execute(new Runnable() {
      @Override
      public void run() {
        KafkaConsumer<Integer, String> consumer = new KafkaConsumer<>(consumerProps);
        consumer.subscribe(Collections.singletonList("messages"));

        while (latch.getCount() > 0) {
          ConsumerRecords<Integer, String> records = consumer.poll(100);
          for (ConsumerRecord<Integer, String> record : records) {
            SpanContext spanContext = TracingKafkaUtils
                .extractSpanContext(record.headers(), tracer);
            assertNotNull(spanContext);
            assertEquals("test", record.value());
            if (key != null) {
              assertEquals(key, record.key());
            }
            consumer.commitSync();
            latch.countDown();
          }
        }
        consumer.close();
      }
    });

    assertTrue(latch.await(30, TimeUnit.SECONDS));

  }

  private Callable<Integer> reportedSpansSize(final MockTracer tracer) {
    return new Callable<Integer>() {
      @Override
      public Integer call() {
        return tracer.finishedSpans().size();
      }
    };
  }

}
