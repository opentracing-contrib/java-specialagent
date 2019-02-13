package io.opentracing.contrib.specialagent.jms;

import static io.opentracing.contrib.specialagent.jms.JmsTest.reportedSpansSize;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertEquals;

import io.opentracing.contrib.specialagent.AgentRunner;
import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;
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
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl;
import org.apache.activemq.artemis.core.remoting.impl.invm.InVMAcceptorFactory;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.impl.ActiveMQServerImpl;
import org.apache.activemq.artemis.jms.client.ActiveMQJMSConnectionFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AgentRunner.class)
@AgentRunner.Config(isolateClassLoader = false)
public class Jms2Test {
  private ActiveMQServer server;
  private Connection connection;
  private Session session;
  private JMSContext jmsContext;

  @Before
  public void before(MockTracer tracer) throws Exception {
    tracer.reset();

    org.apache.activemq.artemis.core.config.Configuration configuration = new ConfigurationImpl();

    HashSet<TransportConfiguration> transports = new HashSet<>();
    transports.add(new TransportConfiguration(InVMAcceptorFactory.class.getName()));
    configuration.setAcceptorConfigurations(transports);
    configuration.setSecurityEnabled(false);

    File targetDir = new File(System.getProperty("user.dir") + "/target");
    configuration.setBrokerInstance(targetDir);

    server = new ActiveMQServerImpl(configuration);
    server.start();
    ActiveMQJMSConnectionFactory connectionFactory = new ActiveMQJMSConnectionFactory("vm://0");
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
  public void sendAndReceive(MockTracer tracer) throws Exception {
    Queue queue = session.createQueue("TEST.FOO");

    MessageProducer producer = session.createProducer(queue);

    MessageConsumer consumer = session.createConsumer(queue);

    TextMessage message = session.createTextMessage("Hello world");

    producer.send(message);

    TextMessage received = (TextMessage) consumer.receive(5000);
    assertEquals("Hello world", received.getText());

    List<MockSpan> mockSpans = tracer.finishedSpans();
    System.out.println(mockSpans.size());
    assertEquals(2, mockSpans.size());
  }

  @Test
  public void sendAndReceiveJMSProducer(MockTracer tracer) throws Exception {
    Destination destination = session.createQueue("TEST.FOO");

    JMSProducer jmsProducer = jmsContext.createProducer();

    MessageConsumer consumer = session.createConsumer(destination);

    TextMessage message = session.createTextMessage("Hello world");

    jmsProducer.send(destination, message);

    TextMessage received = (TextMessage) consumer.receive(5000);
    assertEquals("Hello world", received.getText());

    List<MockSpan> mockSpans = tracer.finishedSpans();
    assertEquals(2, mockSpans.size());
  }

  @Test
  public void sendAndReceiveInListener(final MockTracer tracer) throws Exception {
    Destination destination = session.createQueue("TEST.FOO");

    MessageProducer producer = session.createProducer(destination);

    MessageConsumer messageConsumer = session.createConsumer(destination);

    MessageListener messageListener = new MessageListener() {
      @Override
      public void onMessage(Message message) {
        System.out.println(tracer.activeSpan());
        System.out.println(message);
      }
    };

    messageConsumer.setMessageListener(messageListener);

    TextMessage message = session.createTextMessage("Hello world");

    producer.send(message);

    await().atMost(15, TimeUnit.SECONDS).until(reportedSpansSize(tracer), equalTo(2));

    List<MockSpan> mockSpans = tracer.finishedSpans();
    assertEquals(2, mockSpans.size());
  }
}
