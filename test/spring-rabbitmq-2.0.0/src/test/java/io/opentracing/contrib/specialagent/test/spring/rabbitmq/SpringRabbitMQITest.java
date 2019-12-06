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

package io.opentracing.contrib.specialagent.test.spring.rabbitmq;

import com.rabbitmq.client.Channel;
import io.opentracing.contrib.specialagent.TestUtil;
import java.util.concurrent.CountDownLatch;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.rabbit.listener.adapter.MessageListenerAdapter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class SpringRabbitMQITest {
  static final String topicExchangeName = "spring-boot-exchange";
  static final String queueName = "queue-1";
  static final String queueName2 = "queue-2";

  private static EmbeddedAMQPBroker broker;

  @Autowired
  private RabbitTemplate rabbitTemplate;

  @Bean
  Queue queue() {
    return new Queue(queueName, false);
  }

  @Bean
  public Queue queue2() {
    return new Queue(queueName2);
  }

  @Bean
  TopicExchange exchange() {
    return new TopicExchange(topicExchangeName);
  }

  @Bean
  Binding binding(Queue queue, TopicExchange exchange) {
    return BindingBuilder.bind(queue).to(exchange).with("foo.bar.#");
  }

  @Bean
  SimpleMessageListenerContainer container(ConnectionFactory connectionFactory,
      MessageListenerAdapter listenerAdapter) {
    SimpleMessageListenerContainer container = new SimpleMessageListenerContainer();
    container.setConnectionFactory(connectionFactory);
    container.setQueueNames(queueName);
    container.setMessageListener(listenerAdapter);
    return container;
  }

  @Bean
  public ConnectionFactory connectionFactory() {
    final CachingConnectionFactory factory = new CachingConnectionFactory("localhost");
    factory.setPort(broker.getBrokerPort());
    return factory;
  }

  @Bean
  MessageListenerAdapter listenerAdapter(Receiver receiver) {
    return new MessageListenerAdapter(receiver, "receiveMessage") {
      @Override
      public void onMessage(Message message, Channel channel) throws Exception {
        TestUtil.checkActiveSpan();
        super.onMessage(message, channel);
      }
    };
  }

  @RabbitListener(queues = queueName2)
  public void listen(String message) {
    TestUtil.checkActiveSpan();
    System.out.println("Received <" + message + ">");
  }

  @Bean
  public CommandLineRunner commandLineRunner() {
    return new CommandLineRunner() {
      @Override
      public void run(String... args) throws Exception {
        System.out.println("Sending message to Queue 1...");
        rabbitTemplate.convertAndSend(topicExchangeName, "foo.bar.baz", "Message for Queue 1");

        System.out.println("Sending message to Queue 2...");
        rabbitTemplate.convertAndSend(queueName2, "Message for Queue 2");
      }
    };
  }

  public static void main(final String[] args) throws Exception {
    TestUtil.initTerminalExceptionHandler();

    broker = new EmbeddedAMQPBroker();
    final CountDownLatch latch = TestUtil.initExpectedSpanLatch(6);

    try (final ConfigurableApplicationContext context = SpringApplication.run(SpringRabbitMQITest.class, args)) {
      TestUtil.checkSpan("spring-rabbitmq", 6, latch);
    }

    broker.shutdown();
  }

}