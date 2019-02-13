package io.opentracing.contrib.specialagent.jms;

import static io.opentracing.contrib.specialagent.jms.JmsTest.*;
import static org.awaitility.Awaitility.*;
import static org.hamcrest.core.IsEqual.*;
import static org.junit.Assert.*;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.JMSContext;
import javax.jms.JMSProducer;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl;
import org.apache.activemq.artemis.core.remoting.impl.invm.InVMAcceptorFactory;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.impl.ActiveMQServerImpl;
import org.apache.activemq.artemis.jms.client.ActiveMQJMSConnectionFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.opentracing.contrib.specialagent.AgentRunner;
import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;

@RunWith(AgentRunner.class)
@AgentRunner.Config
public class Jms2Test {
  private ActiveMQServer server;
  private Connection connection;
  private Session session;
  private JMSContext jmsContext;

  @Before
  @SuppressWarnings("resource")
  public void before(final MockTracer tracer) throws Exception {
    tracer.reset();

    final HashSet<TransportConfiguration> transports = new HashSet<>();
    transports.add(new TransportConfiguration(InVMAcceptorFactory.class.getName()));

    final Configuration configuration = new ConfigurationImpl();
    configuration.setAcceptorConfigurations(transports);
    configuration.setSecurityEnabled(false);

    final File targetDir = new File(System.getProperty("user.dir") + "/target");
    configuration.setBrokerInstance(targetDir);

    server = new ActiveMQServerImpl(configuration);
    server.start();

    final ActiveMQJMSConnectionFactory connectionFactory = new ActiveMQJMSConnectionFactory("vm://0");
    connection = connectionFactory.createConnection();
    connection.start();
    jmsContext = connectionFactory.createContext();
    session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
  }

  @After
  public void after() throws Exception {
    jmsContext.close();
    session.close();
    connection.close();
    server.stop();
  }

  @Test
  public void sendAndReceive(final MockTracer tracer) throws Exception {
    final Queue queue = session.createQueue("TEST.FOO");
    final MessageProducer producer = session.createProducer(queue);
    final MessageConsumer consumer = session.createConsumer(queue);
    final TextMessage message = session.createTextMessage("Hello world");

    producer.send(message);

    final TextMessage received = (TextMessage)consumer.receive(5000);
    assertEquals("Hello world", received.getText());

    final List<MockSpan> mockSpans = tracer.finishedSpans();
    System.out.println(mockSpans.size());
    assertEquals(2, mockSpans.size());
  }

  @Test
  public void sendAndReceiveJMSProducer(final MockTracer tracer) throws Exception {
    final Destination destination = session.createQueue("TEST.FOO");
    final JMSProducer jmsProducer = jmsContext.createProducer();
    final MessageConsumer consumer = session.createConsumer(destination);
    final TextMessage message = session.createTextMessage("Hello world");

    jmsProducer.send(destination, message);

    final TextMessage received = (TextMessage) consumer.receive(5000);
    assertEquals("Hello world", received.getText());

    final List<MockSpan> mockSpans = tracer.finishedSpans();
    assertEquals(2, mockSpans.size());
  }

  @Test
  public void sendAndReceiveInListener(final MockTracer tracer) throws Exception {
    final Destination destination = session.createQueue("TEST.FOO");
    final MessageProducer producer = session.createProducer(destination);
    final MessageConsumer messageConsumer = session.createConsumer(destination);
    final MessageListener messageListener = new MessageListener() {
      @Override
      public void onMessage(Message message) {
        System.out.println(tracer.activeSpan());
        System.out.println(message);
      }
    };

    messageConsumer.setMessageListener(messageListener);

    final TextMessage message = session.createTextMessage("Hello world");
    producer.send(message);

    await().atMost(15, TimeUnit.SECONDS).until(reportedSpansSize(tracer), equalTo(2));

    final List<MockSpan> mockSpans = tracer.finishedSpans();
    assertEquals(2, mockSpans.size());
  }
}
