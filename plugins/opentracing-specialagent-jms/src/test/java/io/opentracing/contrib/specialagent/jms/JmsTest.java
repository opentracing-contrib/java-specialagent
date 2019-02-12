package io.opentracing.contrib.specialagent.jms;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import io.opentracing.contrib.jms.TracingMessageProducer;
import io.opentracing.contrib.jms.common.TracingMessageConsumer;
import io.opentracing.contrib.specialagent.AgentRunner;
import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AgentRunner.class)
@AgentRunner.Config(isolateClassLoader = false)
public class JmsTest {
  private Session session;
  private Connection connection;

  @Before
  public void before(final MockTracer tracer) throws JMSException {
    tracer.reset();
    ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory(
        "vm://localhost?broker.persistent=false");
    connection = connectionFactory.createConnection();
    connection.start();
    session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
  }

  @After
  public void after() throws JMSException {
    session.close();
    connection.close();
  }

  @Test
  public void sendAndReceive(final MockTracer tracer) throws JMSException {
    Destination destination = session
        .createQueue("TEST.FOO");

    MessageProducer producer = session.createProducer(destination);
    producer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
    assertTrue(producer instanceof TracingMessageProducer);

    MessageConsumer consumer = session.createConsumer(destination);
    assertTrue(consumer instanceof TracingMessageConsumer);

    TextMessage message = session.createTextMessage("Hello world");

    producer.send(message);

    TextMessage received = (TextMessage) consumer.receive(5000L);
    assertEquals("Hello world", received.getText());

    List<MockSpan> mockSpans = tracer.finishedSpans();
    System.out.println(mockSpans.size());
    assertEquals(2, mockSpans.size());
  }

  @Test
  public void sendAndReceiveInListener(final MockTracer tracer) throws Exception {
    Destination destination = session.createQueue("TEST.FOO");

    MessageProducer producer = session.createProducer(destination);
    producer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);

    MessageConsumer consumer = session.createConsumer(destination);

    MessageListener messageListener = new MessageListener() {
      @Override
      public void onMessage(Message message) {
        System.out.println(tracer.activeSpan());
        System.out.println(message);
      }
    };

    consumer.setMessageListener(messageListener);

    TextMessage message = session.createTextMessage("Hello world");

    producer.send(message);

    await().atMost(15, TimeUnit.SECONDS).until(reportedSpansSize(tracer), equalTo(2));

    List<MockSpan> mockSpans = tracer.finishedSpans();
    assertEquals(2, mockSpans.size());
  }

  private Callable<Integer> reportedSpansSize(final MockTracer tracer) {
    return new Callable<Integer>() {
      @Override
      public Integer call() {
        return tracer.finishedSpans().size();
      }
    };
  }
}
