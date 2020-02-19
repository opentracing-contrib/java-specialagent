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

package io.opentracing.contrib.specialagent.test.jms2;

import io.opentracing.contrib.specialagent.TestUtil.ComponentSpanCount;
import java.io.File;
import java.util.HashSet;

import javax.jms.Connection;
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

import io.opentracing.contrib.specialagent.TestUtil;

public class Jms2ITest {
  public static void main(final String[] args) throws Exception {
    final HashSet<TransportConfiguration> transports = new HashSet<>();
    transports.add(new TransportConfiguration(InVMAcceptorFactory.class.getName()));

    final Configuration configuration = new ConfigurationImpl();
    configuration.setAcceptorConfigurations(transports);
    configuration.setSecurityEnabled(false);

    final File targetDir = new File(new File("").getAbsoluteFile(), "target");
    configuration.setBrokerInstance(targetDir);

    final ActiveMQServer server = new ActiveMQServerImpl(configuration);
    server.start();

    try (final ActiveMQJMSConnectionFactory connectionFactory = new ActiveMQJMSConnectionFactory("vm://0")) {
      final Connection connection = connectionFactory.createConnection();
      connection.start();
      try (
        final JMSContext context = connectionFactory.createContext();
        final Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      ) {
        System.out.println("Session: " + session);
        final Destination destination = session.createQueue("TEST.FOO2");

        final JMSProducer producer = context.createProducer();
        System.out.println("PRODUCER: " + producer);

        final MessageConsumer consumer = session.createConsumer(destination);
        System.out.println("CONSUMER: " + consumer);

        final TextMessage message = session.createTextMessage("Hello world");
        producer.send(destination, message);

        final TextMessage received = (TextMessage)consumer.receive(5000);
        System.out.println("RECEIVED: " + received.getText());
      }
    }

    server.stop(true);
    TestUtil.checkSpan(new ComponentSpanCount("java-jms", 2, true));
    System.exit(0);
  }
}