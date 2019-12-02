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

package io.opentracing.contrib.specialagent.rule.jms1;

import static org.awaitility.Awaitility.*;
import static org.hamcrest.core.IsEqual.*;
import static org.junit.Assert.*;

import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.Destination;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.junit.Test;

import io.opentracing.contrib.jms.TracingMessageProducer;
import io.opentracing.contrib.jms.common.TracingMessageConsumer;
import io.opentracing.contrib.specialagent.Logger;
import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;

//NOTE: This class is copied from specialagent-jms-2.
//NOTE: It should be a 100% duplicate!
public abstract class JmsTest {
  static final Logger logger = Logger.getLogger(JmsTest.class);

  Session session;
  Connection connection;

  @Test
  public void sendAndReceive(final MockTracer tracer) throws Exception {
    final Destination destination = session.createQueue("TEST.FOO");

    final MessageProducer producer = session.createProducer(destination);
    producer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
    assertTrue(producer instanceof TracingMessageProducer);

    final MessageConsumer consumer = session.createConsumer(destination);
    assertTrue(consumer instanceof TracingMessageConsumer);

    final TextMessage message = session.createTextMessage("Hello world");
    producer.send(message);

    final TextMessage received = (TextMessage)consumer.receive(5000);
    assertEquals("Hello world", received.getText());

    final List<MockSpan> finishedSpans = tracer.finishedSpans();
    assertEquals(2, finishedSpans.size());
  }

  @Test
  public void sendAndReceiveInListener(final MockTracer tracer) throws Exception {
    final Destination destination = session.createQueue("TEST.FOO");
    final MessageProducer producer = session.createProducer(destination);
    producer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);

    final MessageConsumer consumer = session.createConsumer(destination);
    final MessageListener messageListener = new MessageListener() {
      @Override
      public void onMessage(final Message message) {
        logger.fine("onMessage[" + tracer.activeSpan() + "]: " + message);
      }
    };

    consumer.setMessageListener(messageListener);

    final TextMessage message = session.createTextMessage("Hello world");
    producer.send(message);

    await().atMost(15, TimeUnit.SECONDS).until(() -> tracer.finishedSpans().size(), equalTo(2));

    final List<MockSpan> finishedSpans = tracer.finishedSpans();
    assertEquals(2, finishedSpans.size());
  }
}