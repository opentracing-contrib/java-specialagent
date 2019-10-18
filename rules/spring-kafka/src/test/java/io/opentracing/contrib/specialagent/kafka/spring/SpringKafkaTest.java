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

import static org.awaitility.Awaitility.*;
import static org.hamcrest.core.IsEqual.*;
import static org.junit.Assert.*;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.test.rule.EmbeddedKafkaRule;
import org.springframework.kafka.test.utils.KafkaTestUtils;

import io.opentracing.contrib.specialagent.AgentRunner;
import io.opentracing.contrib.specialagent.AgentRunner.Config;
import io.opentracing.mock.MockTracer;
import io.opentracing.util.GlobalTracer;

@RunWith(AgentRunner.class)
@Config(isolateClassLoader = false)
public class SpringKafkaTest {
  private static final AtomicInteger counter = new AtomicInteger();

  @ClassRule
  public static final EmbeddedKafkaRule embeddedKafkaRule = new EmbeddedKafkaRule(2, true, 2, "messages");

  @Before
  public void before(final MockTracer tracer) {
    tracer.reset();
  }

  @Test
  public void test(final MockTracer tracer) {
    final ApplicationContext context = new AnnotationConfigApplicationContext(KafkaConfiguration.class);
    KafkaTemplate<Integer,String> kafkaTemplate = context.getBean(KafkaTemplate.class);
    kafkaTemplate.send("spring", "message");

    await().atMost(15, TimeUnit.SECONDS).until(() -> tracer.finishedSpans().size(), equalTo(1));
    assertEquals(1, counter.get());
    assertEquals(1, tracer.finishedSpans().size());
  }

  @Configuration
  @EnableKafka
  public static class KafkaConfiguration {
    @Bean
    public ConcurrentKafkaListenerContainerFactory<Integer,String> kafkaListenerContainerFactory() {
      final ConcurrentKafkaListenerContainerFactory<Integer,String> factory = new ConcurrentKafkaListenerContainerFactory<>();
      factory.setConsumerFactory(consumerFactory());
      return factory;
    }

    @Bean
    public ConsumerFactory<Integer,String> consumerFactory() {
      final Map<String,Object> consumerProps = KafkaTestUtils.consumerProps("sampleRawConsumer", "false", embeddedKafkaRule.getEmbeddedKafka());
      consumerProps.put("auto.offset.reset", "earliest");
      return new DefaultKafkaConsumerFactory<>(consumerProps);
    }

    @Bean
    public ProducerFactory<Integer,String> producerFactory() {
      return new DefaultKafkaProducerFactory<>(KafkaTestUtils.producerProps(embeddedKafkaRule.getEmbeddedKafka()));
    }

    @Bean
    public KafkaTemplate<Integer,String> kafkaTemplate() {
      return new KafkaTemplate<>(producerFactory());
    }

    @KafkaListener(topics = "spring")
    public void listen(final String message) {
      assertNotNull(GlobalTracer.get().activeSpan());
      assertEquals("message", message);
      counter.incrementAndGet();
    }
  }
}