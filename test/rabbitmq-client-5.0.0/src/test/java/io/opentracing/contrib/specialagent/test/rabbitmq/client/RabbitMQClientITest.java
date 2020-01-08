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

package io.opentracing.contrib.specialagent.test.rabbitmq.client;

import java.util.concurrent.CountDownLatch;

import com.rabbitmq.client.CancelCallback;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;
import com.rabbitmq.client.Delivery;

import io.opentracing.contrib.specialagent.TestUtil;

public class RabbitMQClientITest {
  private static final String QUEUE_NAME = "queue";
  private static final String message = "Hello World!";

  public static void main(final String[] args) throws Exception {
    final CountDownLatch latch = TestUtil.initExpectedSpanLatch(2);

    final EmbeddedAMQPBroker broker = new EmbeddedAMQPBroker();
    final ConnectionFactory factory = new ConnectionFactory();
    factory.setUsername("guest");
    factory.setPassword("guest");
    factory.setHost("localhost");
    factory.setPort(broker.getBrokerPort());
    try (
      final Connection connection = factory.newConnection();
      final Channel channel = connection.createChannel();
    ) {
      channel.queueDeclare(QUEUE_NAME, false, false, false, null);
      channel.basicPublish("", QUEUE_NAME, null, message.getBytes());
      System.out.println(" [x] Sent '" + message + "'");

      final DeliverCallback deliverCallback = new DeliverCallback() {
        @Override
        public void handle(final String s, final Delivery delivery) {
          TestUtil.checkActiveSpan();
        }
      };

      channel.basicConsume(QUEUE_NAME, true, deliverCallback, new CancelCallback() {
        @Override
        public void handle(final String s) {
        }
      });

      TestUtil.checkSpan("java-rabbitmq", 2, latch);
    }

    broker.shutdown();
  }
}