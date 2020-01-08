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

package io.opentracing.contrib.specialagent.test.spring.kafka;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.rule.EmbeddedKafkaRule;
import org.springframework.kafka.test.utils.KafkaTestUtils;

import io.opentracing.contrib.specialagent.TestUtil;

@SpringBootApplication
public class SpringKafkaITest {
  private static EmbeddedKafkaBroker kafkaEmbedded;

  @Autowired
  public Producer producer;

  public static void main(final String[] args) throws Exception {
    final EmbeddedKafkaRule embeddedKafkaRule = TestUtil.retry(new Callable<EmbeddedKafkaRule>() {
      @Override
      public EmbeddedKafkaRule call() {
        final EmbeddedKafkaRule rule = new EmbeddedKafkaRule(1, true, 2, "users", "reply");
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

    kafkaEmbedded = embeddedKafkaRule.getEmbeddedKafka();

    final CountDownLatch latch = TestUtil.initExpectedSpanLatch(6);

    try (final ConfigurableApplicationContext context = SpringApplication.run(SpringKafkaITest.class, args)) {
      TestUtil.checkSpan("java-kafka", 6, latch);
    }
    embeddedKafkaRule.after();

    // Embedded Kafka and Zookeeper processes may not exit
    System.exit(0);
  }

  @Bean
  public ProducerFactory<String,String> producerFactory() {
    return new DefaultKafkaProducerFactory<>(KafkaTestUtils.producerProps(kafkaEmbedded));
  }

  @Bean
  public ConsumerFactory<String,String> consumerFactory() {
    final Map<String,Object> consumerProps = KafkaTestUtils.consumerProps("sampleRawConsumer", "false", kafkaEmbedded);
    consumerProps.put("auto.offset.reset", "earliest");
    return new DefaultKafkaConsumerFactory<>(consumerProps);
  }

  @Bean
  public ConcurrentKafkaListenerContainerFactory<String,String> kafkaListenerContainerFactory() {
    final ConcurrentKafkaListenerContainerFactory<String,String> factory = new ConcurrentKafkaListenerContainerFactory<>();
    factory.setConsumerFactory(consumerFactory());
    factory.setReplyTemplate(kafkaTemplate());
    return factory;
  }

  @Bean
  public KafkaTemplate<String,String> kafkaTemplate() {
    return new KafkaTemplate<>(producerFactory());
  }

  @Bean
  public CommandLineRunner commandLineRunner() {
    return new CommandLineRunner() {
      @Override
      public void run(final String ... args) {
        producer.sendMessage("Message");
      }
    };
  }
}