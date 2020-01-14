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

package io.opentracing.contrib.specialagent.test.spring.jms;

import java.util.concurrent.CountDownLatch;

import javax.jms.ConnectionFactory;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jms.DefaultJmsListenerContainerFactoryConfigurer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;
import org.springframework.jms.config.JmsListenerContainerFactory;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.support.converter.MappingJackson2MessageConverter;
import org.springframework.jms.support.converter.MessageConverter;
import org.springframework.jms.support.converter.MessageType;

import io.opentracing.contrib.specialagent.TestUtil;

@SpringBootApplication
@EnableJms
public class SpringJMSITest {
  public static void main(final String[] args) throws Exception {
    final CountDownLatch latch = TestUtil.initExpectedSpanLatch(3);
    try (final ConfigurableApplicationContext context = SpringApplication.run(SpringJMSITest.class, args)) {
      TestUtil.checkSpan("java-jms", 3, latch);
    }
  }

  @Autowired
  private JmsTemplate jmsTemplate;

  @Bean
  public JmsListenerContainerFactory<?> myFactory(final DefaultJmsListenerContainerFactoryConfigurer configurer) {
    final DefaultJmsListenerContainerFactory factory = new DefaultJmsListenerContainerFactory();
    configurer.configure(factory, connectionFactory());
    return factory;
  }

  @Bean
  public ConnectionFactory connectionFactory() {
    return new ActiveMQConnectionFactory("vm://localhost?broker.persistent=false");
  }

  @Bean
  public MessageConverter jacksonJmsMessageConverter() {
    final MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
    converter.setTargetType(MessageType.TEXT);
    converter.setTypeIdPropertyName("_type");
    return converter;
  }

  @JmsListener(destination = "mailbox", containerFactory = "myFactory")
  public void receiveMessage(final String message) {
    System.out.println("Received <" + message + ">");
    TestUtil.checkActiveSpan();
  }

  @Bean
  public CommandLineRunner commandLineRunner() {
    return new CommandLineRunner() {
      @Override
      public void run(final String ... args) {
        jmsTemplate.convertAndSend("mailbox", "hello");
      }
    };
  }
}