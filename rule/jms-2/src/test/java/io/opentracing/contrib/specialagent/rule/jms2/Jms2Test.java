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

package io.opentracing.contrib.specialagent.rule.jms2;

import static org.junit.Assert.*;

import java.io.File;
import java.util.HashSet;
import java.util.List;

import javax.jms.DeliveryMode;
import javax.jms.Destination;
import javax.jms.JMSContext;
import javax.jms.JMSProducer;
import javax.jms.MessageConsumer;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl;
import org.apache.activemq.artemis.core.remoting.impl.invm.InVMAcceptorFactory;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.impl.ActiveMQServerImpl;
import org.apache.activemq.artemis.jms.client.ActiveMQJMSConnectionFactory;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.opentracing.contrib.specialagent.AgentRunner;
import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;

@RunWith(AgentRunner.class)
public class Jms2Test extends JmsTest {
  private static ActiveMQServer server;
  private static JMSContext context;

  @Before
  public void before(final MockTracer tracer) {
    tracer.reset();
  }

  @BeforeClass
  @SuppressWarnings("resource")
  public static void startActiveMQ() throws Exception {
    final HashSet<TransportConfiguration> transports = new HashSet<>();
    transports.add(new TransportConfiguration(InVMAcceptorFactory.class.getName()));

    final Configuration configuration = new ConfigurationImpl();
    configuration.setAcceptorConfigurations(transports);
    configuration.setSecurityEnabled(false);

    final File targetDir = new File(new File("").getAbsoluteFile(), "target");
    configuration.setBrokerInstance(targetDir);

    server = new ActiveMQServerImpl(configuration);
    server.start();

    final ActiveMQJMSConnectionFactory connectionFactory = new ActiveMQJMSConnectionFactory("vm://0");
    connection = connectionFactory.createConnection();
    connection.start();
    context = connectionFactory.createContext();
    session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
  }

  @AfterClass
  public static void stopActiveMQ() throws Exception {
    context.close();
    session.close();
    connection.close();
    server.stop();
  }

  @Test
  public void sendAndReceiveJMSProducer(final MockTracer tracer) throws Exception {
    final Destination destination = session.createQueue("TEST.JMS2.JMSPRODUCER");

    try(final MessageConsumer consumer = session.createConsumer(destination)) {
      final TextMessage message = session.createTextMessage("Hello world");

      final JMSProducer producer = context.createProducer();
      producer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
      producer.send(destination, message);

      final TextMessage received = (TextMessage) consumer.receive(5000);
      assertEquals("Hello world", received.getText());
    }

    final List<MockSpan> finishedSpans = tracer.finishedSpans();
    assertEquals(2, finishedSpans.size());
  }
}