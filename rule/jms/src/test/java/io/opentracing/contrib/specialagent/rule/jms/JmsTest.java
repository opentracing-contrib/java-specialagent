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

package io.opentracing.contrib.specialagent.rule.jms;

import static org.awaitility.Awaitility.*;
import static org.hamcrest.core.IsEqual.*;
import static org.junit.Assert.*;

import io.opentracing.contrib.specialagent.AgentRunner;
import java.util.List;
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
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import io.opentracing.contrib.common.WrapperProxy;
import io.opentracing.contrib.jms.TracingMessageProducer;
import io.opentracing.contrib.jms.common.TracingMessageConsumer;
import io.opentracing.contrib.specialagent.Logger;
import io.opentracing.contrib.specialagent.TestUtil;
import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;
import org.junit.runner.RunWith;

@RunWith(AgentRunner.class)
public class JmsTest {
  static final Logger logger = Logger.getLogger(JmsTest.class);

  static Session session;
  static Connection connection;

  @Before
  public void before(final MockTracer tracer) {
    tracer.reset();
  }

  @BeforeClass
  public static void startActiveMQ() throws JMSException {
    final ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory("vm://localhost?broker.persistent=false");
    connection = connectionFactory.createConnection();
    connection.start();
    session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
  }

  @AfterClass
  public static void stopActiveMQ() throws JMSException {
    session.close();
    connection.close();
  }

  @Test
  public void sendAndReceive(final MockTracer tracer) throws Exception {
    final Destination destination = session.createQueue("TEST.JMS1.RECEIVE");

    final MessageConsumer consumer = session.createConsumer(destination);
    assertTrue(WrapperProxy.isWrapper(consumer, TracingMessageConsumer.class));

    final TextMessage message = session.createTextMessage("Hello world");

    final MessageProducer producer = session.createProducer(destination);
    producer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
    assertTrue(WrapperProxy.isWrapper(producer, TracingMessageProducer.class));
    producer.send(message);

    final TextMessage received = (TextMessage)consumer.receive(5000);
    assertEquals("Hello world", received.getText());

    final List<MockSpan> finishedSpans = tracer.finishedSpans();
    assertEquals(2, finishedSpans.size());

    producer.close();
    consumer.close();
  }

  @Test
  public void sendAndReceiveInListener(final MockTracer tracer) throws Exception {
    final Destination destination = session.createQueue("TEST.JMS1.LISTENER");

    final MessageConsumer consumer = session.createConsumer(destination);
    final MessageListener messageListener = new MessageListener() {
      @Override
      public void onMessage(final Message message) {
        logger.fine("onMessage[" + tracer.activeSpan() + "]: " + message);
      }
    };

    consumer.setMessageListener(messageListener);

    final TextMessage message = session.createTextMessage("Hello world");

    final MessageProducer producer = session.createProducer(destination);
    producer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
    producer.send(message);

    await().atMost(15, TimeUnit.SECONDS).until(TestUtil.reportedSpansSize(tracer), equalTo(2));

    final List<MockSpan> finishedSpans = tracer.finishedSpans();
    assertEquals(2, finishedSpans.size());

    producer.close();
    consumer.close();
  }
}