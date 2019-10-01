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

package io.opentracing.contrib.specialagent.rabbitmq;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.GetResponse;

import io.opentracing.contrib.specialagent.AgentRunner;
import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;

@RunWith(AgentRunner.class)
@AgentRunner.Config(isolateClassLoader = false)
public class RabbitMQTest {
  private static EmbeddedAMQPBroker embeddedAMQPBroker;
  private Connection connection;
  private Channel channel;

  @BeforeClass
  public static void beforeClass() throws Exception {
    embeddedAMQPBroker = new EmbeddedAMQPBroker();
  }

  @AfterClass
  public static void afterClass() {
    if (embeddedAMQPBroker != null)
      embeddedAMQPBroker.shutdown();
  }

  @Before
  public void before(final MockTracer tracer) throws IOException, TimeoutException {
    tracer.reset();
    final ConnectionFactory factory = new ConnectionFactory();
    factory.setUsername("guest");
    factory.setPassword("guest");
    factory.setHost("localhost");
    factory.setPort(embeddedAMQPBroker.getBrokerPort());
    connection = factory.newConnection();
    channel = connection.createChannel();
  }

  @After
  public void after() throws IOException, TimeoutException {
    if (channel != null)
      channel.close();

    if (connection != null)
      connection.close();
  }

  @Test
  public void basicGet(final MockTracer tracer) throws IOException {
    final String exchangeName = "basicGetExchange";
    final String queueName = "basicGetQueue";
    final String routingKey = "#";

    channel.exchangeDeclare(exchangeName, "direct", true);
    channel.queueDeclare(queueName, true, false, false, null);
    channel.queueBind(queueName, exchangeName, routingKey);

    final byte[] messageBodyBytes = "Hello, world!".getBytes();
    channel.basicPublish(exchangeName, routingKey, null, messageBodyBytes);

    final GetResponse response = channel.basicGet(queueName, false);
    assertNotNull(response.getBody());

    final List<MockSpan> finishedSpans = tracer.finishedSpans();
    assertEquals(2, finishedSpans.size());

    assertNull(tracer.activeSpan());
  }

  @Test
  public void basicConsume(final MockTracer tracer) throws IOException, InterruptedException {
    final String exchangeName = "basicConsumeExchange";
    final String queueName = "basicConsumeQueue";
    final String routingKey = "#";

    channel.exchangeDeclare(exchangeName, "direct", true);
    channel.queueDeclare(queueName, true, false, false, null);
    channel.queueBind(queueName, exchangeName, routingKey);

    final byte[] messageBodyBytes = "Hello, world!".getBytes();
    channel.basicPublish(exchangeName, routingKey, null, messageBodyBytes);

    final CountDownLatch latch = new CountDownLatch(1);
    channel.basicConsume(queueName, false, new DefaultConsumer(channel) {
      @Override
      public void handleDelivery(final String consumerTag, final Envelope envelope, final AMQP.BasicProperties properties, final byte[] body) throws IOException {
        final long deliveryTag = envelope.getDeliveryTag();
        channel.basicAck(deliveryTag, false);
        latch.countDown();
      }
    });

    latch.await(15, TimeUnit.SECONDS);
    List<MockSpan> finishedSpans = tracer.finishedSpans();
    for (int tries = 10; tries > 0 && finishedSpans.size() < 2; --tries) {
      TimeUnit.SECONDS.sleep(1L);
      finishedSpans = tracer.finishedSpans();
    }

    assertEquals(2, finishedSpans.size());
    assertNull(tracer.activeSpan());
  }
}