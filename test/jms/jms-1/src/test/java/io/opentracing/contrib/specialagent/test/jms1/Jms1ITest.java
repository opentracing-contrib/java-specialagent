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

package io.opentracing.contrib.specialagent.test.jms1;

import java.util.concurrent.CountDownLatch;

import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.Destination;
import javax.jms.ExceptionListener;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.apache.activemq.ActiveMQConnectionFactory;

import io.opentracing.contrib.specialagent.TestUtil;
import io.opentracing.contrib.specialagent.TestUtil.ComponentSpanCount;

public class Jms1ITest {
  private static final int threadCount = 4;
  private static final CountDownLatch latch = new CountDownLatch(threadCount);

  public static void main(final String[] args) throws Exception {
    final ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory("vm://localhost?broker.persistent=false");
    final Connection connection = connectionFactory.createConnection();
    connection.start();

    for (int i = 0; i < threadCount; ++i)
      new Thread(new HelloWorldProducer(connection)).start();

    latch.await();
    TestUtil.checkSpan(new ComponentSpanCount("java-jms", 4));
    connection.close();
  }

  public static class HelloWorldProducer implements Runnable {
    private final Connection connection;

    public HelloWorldProducer(final Connection connection) {
      this.connection = connection;
    }

    @Override
    public void run() {
      try {
        // Create a Session
        final Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        System.out.println("Session: " + session);

        // Create the destination (Topic or Queue)
        final Destination destination = session.createQueue("TEST.FOO");

        // Create a MessageProducer from the Session to the Topic or Queue
        final MessageProducer producer = session.createProducer(destination);
        producer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);

        System.out.println("PRODUCER: " + producer);

        // Create a messages
        final String text = "Hello world! From: " + Thread.currentThread().getName() + " : " + this.hashCode();
        TextMessage message = session.createTextMessage(text);

        // Tell the producer to send the message
        System.out.println("Sent message: " + message.hashCode() + " : " + Thread.currentThread().getName());
        producer.send(message);

        // Clean up
        session.close();
      }
      catch (final Exception e) {
        throw new RuntimeException(e);
      }
      finally {
        latch.countDown();
      }
    }
  }

  public static class HelloWorldConsumer implements Runnable, ExceptionListener {
    private final Connection connection;

    public HelloWorldConsumer(final Connection connection) {
      this.connection = connection;
    }

    @Override
    public void run() {
      try {
        // Create a Session
        final Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

        // Create the destination (Topic or Queue)
        final Destination destination = session.createQueue("TEST.FOO");

        // Create a MessageConsumer from the Session to the Topic or Queue
        final MessageConsumer consumer = session.createConsumer(destination);
        System.out.println("CONSUMER: " + consumer);

        // Wait for a message
        final Message message = consumer.receive(1000);
        if (message instanceof TextMessage) {
          final TextMessage textMessage = (TextMessage)message;
          final String text = textMessage.getText();
          System.out.println("Received: " + text);
        }
        else {
          System.out.println("Received: " + message);
        }

        consumer.close();
        session.close();
      }
      catch (final Exception e) {
        throw new RuntimeException(e);
      }
      finally {
        latch.countDown();
      }
    }

    @Override
    public synchronized void onException(final JMSException e) {
      System.out.println("JMS Exception occured. Shutting down client.");
    }
  }
}