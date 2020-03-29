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
package io.opentracing.contrib.specialagent.rule.spring.rabbitmq;

import static org.awaitility.Awaitility.*;
import static org.hamcrest.core.IsEqual.*;
import static org.junit.Assert.*;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.rabbit.listener.adapter.MessageListenerAdapter;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.opentracing.contrib.specialagent.AgentRunner;
import io.opentracing.contrib.specialagent.TestUtil;
import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;
import io.opentracing.util.GlobalTracer;

@RunWith(AgentRunner.class)
public class SpringRabbitMQTest {
  private static final String QUEUE_NAME = "queue-test";
  private static final String QUEUE_NAME2 = "queue-test-2";
  private static final AtomicInteger counter = new AtomicInteger();
  private static EmbeddedAMQPBroker embeddedAMQPBroker;

  @BeforeClass
  public static void beforeClass() throws Exception {
    embeddedAMQPBroker = new EmbeddedAMQPBroker();
  }

  @AfterClass
  public static void afterClass() {
    if (embeddedAMQPBroker != null)
      embeddedAMQPBroker.shutdown();
  }

  @Test
  public void test(final MockTracer tracer) {
    try (final AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(RabbitConfiguration.class)) {
      final AmqpTemplate template = context.getBean(AmqpTemplate.class);
      template.convertAndSend(QUEUE_NAME, "message");
      template.convertAndSend(QUEUE_NAME2, "message-2");

      await().atMost(15, TimeUnit.SECONDS).until(TestUtil.reportedSpansSize(tracer), equalTo(2));

      assertEquals(2, counter.get());
      final List<MockSpan> spans = tracer.finishedSpans();
      assertEquals(2, spans.size());
    }
  }

  @Configuration
  @EnableRabbit
  public static class RabbitConfiguration {
    @Bean
    public ConnectionFactory connectionFactory() {
      return new CachingConnectionFactory("localhost", embeddedAMQPBroker.getBrokerPort());
    }

    @Bean
    public AmqpAdmin amqpAdmin() {
      return new RabbitAdmin(connectionFactory());
    }

    @Bean
    public RabbitTemplate rabbitTemplate() {
      return new RabbitTemplate(connectionFactory());
    }

    @Bean
    public Queue queue() {
      return new Queue(QUEUE_NAME);
    }

    @Bean
    public Queue queue2() {
      return new Queue(QUEUE_NAME2);
    }

    @Bean
    SimpleMessageListenerContainer container(final ConnectionFactory connectionFactory, final MessageListenerAdapter listenerAdapter) {
      final SimpleMessageListenerContainer container = new SimpleMessageListenerContainer();
      container.setConnectionFactory(connectionFactory);
      container.setQueueNames(QUEUE_NAME2);
      container.setMessageListener(listenerAdapter);
      return container;
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory() {
      final SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
      factory.setConnectionFactory(connectionFactory());
      factory.setConcurrentConsumers(3);
      factory.setMaxConcurrentConsumers(10);
      return factory;
    }

    @Bean
    Receiver receiver() {
      return new Receiver();
    }

    @RabbitListener(queues = QUEUE_NAME)
    public void listen(final String message) {
      assertNotNull(GlobalTracer.get().activeSpan());
      assertEquals("message", message);
      counter.incrementAndGet();
    }

    @Bean
    MessageListenerAdapter listenerAdapter(final Receiver receiver) {
      return new MessageListenerAdapter(receiver, "receiveMessage");
    }

    public static class Receiver {
      public void receiveMessage(final String message) {
        assertNotNull(GlobalTracer.get().activeSpan());
        assertEquals("message-2", message);
        counter.incrementAndGet();
      }
    }
  }
}