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

package io.opentracing.contrib.specialagent.jms.spring;

import static org.awaitility.Awaitility.*;
import static org.hamcrest.core.IsEqual.*;
import static org.junit.Assert.*;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.jms.ConnectionFactory;

import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl;
import org.apache.activemq.artemis.core.remoting.impl.invm.InVMAcceptorFactory;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.impl.ActiveMQServerImpl;
import org.apache.activemq.artemis.jms.client.ActiveMQJMSConnectionFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.support.converter.MessageConverter;
import org.springframework.jms.support.converter.SimpleMessageConverter;

import io.opentracing.contrib.specialagent.AgentRunner;
import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;
import io.opentracing.util.GlobalTracer;

@RunWith(AgentRunner.class)
@AgentRunner.Config(isolateClassLoader = false)
public class SpringJmsTest {
  private static final AtomicInteger counter = new AtomicInteger();
  private ActiveMQServer server;

  @Before
  public void before(final MockTracer tracer) throws Exception {
    tracer.reset();

    final HashSet<TransportConfiguration> transports = new HashSet<>();
    transports.add(new TransportConfiguration(InVMAcceptorFactory.class.getName()));

    final ConfigurationImpl configuration = new ConfigurationImpl();
    configuration.setAcceptorConfigurations(transports);
    configuration.setSecurityEnabled(false);

    final File targetDir = new File(new File("").getAbsoluteFile(), "target");
    configuration.setBrokerInstance(targetDir);

    server = new ActiveMQServerImpl(configuration);
    server.start();
  }

  @After
  public void after() throws Exception {
    server.stop();
  }

  @Test
  public void test(final MockTracer tracer) {
    final ApplicationContext context = new AnnotationConfigApplicationContext(RabbitConfiguration.class);
    final JmsTemplate jmsTemplate = context.getBean(JmsTemplate.class);
    jmsTemplate.convertAndSend("mailbox", "message");

    await().atMost(15, TimeUnit.SECONDS).until(reportedSpansSize(tracer), equalTo(1));

    assertEquals(1, counter.get());
    final List<MockSpan> spans = tracer.finishedSpans();
    assertEquals(1, spans.size());
  }

  private static Callable<Integer> reportedSpansSize(final MockTracer tracer) {
    return new Callable<Integer>() {
      @Override
      public Integer call() {
        return tracer.finishedSpans().size();
      }
    };
  }

  @EnableJms
  @Configuration
  public static class RabbitConfiguration {
    @Bean
    public DefaultJmsListenerContainerFactory jmsListenerContainerFactory() {
      final DefaultJmsListenerContainerFactory factory = new DefaultJmsListenerContainerFactory();
      factory.setConnectionFactory(connectionFactory());
      return factory;
    }

    @Bean
    public ConnectionFactory connectionFactory() {
      return new ActiveMQJMSConnectionFactory("vm://0");
    }

    @Bean
    public MessageConverter messageConverter() {
      return new SimpleMessageConverter();
    }

    @Bean
    public JmsTemplate jmsTemplate() {
      return new JmsTemplate(connectionFactory());
    }

    @JmsListener(destination = "mailbox", containerFactory = "jmsListenerContainerFactory")
    public void receiveMessage(String message) {
      assertNotNull(GlobalTracer.get().activeSpan());
      assertEquals("message", message);
      counter.incrementAndGet();
    }
  }
}