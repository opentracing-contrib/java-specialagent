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

package io.opentracing.contrib.specialagent.lettuce;

import static org.awaitility.Awaitility.*;
import static org.hamcrest.core.IsEqual.*;
import static org.junit.Assert.*;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.lettuce.core.ConnectionFuture;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.codec.StringCodec;
import io.lettuce.core.pubsub.RedisPubSubAdapter;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import io.lettuce.core.pubsub.api.sync.RedisPubSubCommands;
import io.opentracing.contrib.specialagent.AgentRunner;
import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;
import redis.embedded.RedisServer;

@RunWith(AgentRunner.class)
public class Lettuce50Test {
  private static final String address = "redis://localhost";
  private static RedisServer server;
  private static RedisClient client;

  @BeforeClass
  public static void beforeClass() throws IOException {
    server = new RedisServer();
    server.start();
    client = RedisClient.create(address);
  }

  @AfterClass
  public static void afterClass() {
    if (client != null)
      client.shutdown();

    if (server != null)
      server.stop();
  }

  @Before
  public void before(final MockTracer tracer) {
    tracer.reset();
  }

  @Test
  public void testPubSub(final MockTracer tracer) {
    final StatefulRedisPubSubConnection<String,String> connection = client.connectPubSub();
    connection.addListener(new RedisPubSubAdapter<>());

    final RedisPubSubCommands<String,String> commands = connection.sync();
    commands.subscribe("channel");

    final RedisCommands<String,String> commands2 = client.connect().sync();
    commands2.publish("channel", "msg");

    await().atMost(15, TimeUnit.SECONDS).until(() -> tracer.finishedSpans().size(), equalTo(4));

    client.shutdown();

    final List<MockSpan> spans = tracer.finishedSpans();
    assertEquals(4, spans.size());
  }

  @Test
  public void testSync(final MockTracer tracer) {
    try (final StatefulRedisConnection<String,String> connection = client.connect()) {
      final RedisCommands<String,String> commands = connection.sync();
      assertEquals("OK", commands.set("key", "value"));
      assertEquals("value", commands.get("key"));
    }

    final List<MockSpan> spans = tracer.finishedSpans();
    assertEquals(2, spans.size());
  }

  @Test
  public void testConnectAsync(final MockTracer tracer) throws Exception {
    final ConnectionFuture<StatefulRedisConnection<String,String>> connectionFuture = client.connectAsync(StringCodec.UTF8, RedisURI.create(address));
    try (final StatefulRedisConnection<String,String> connection = connectionFuture.get(10, TimeUnit.SECONDS)) {
      final RedisCommands<String,String> commands = connection.sync();
      assertEquals("OK", commands.set("key", "value"));
      assertEquals("value", commands.get("key"));
    }

    final List<MockSpan> spans = tracer.finishedSpans();
    assertEquals(2, spans.size());
  }

  @Test
  public void testAsync(final MockTracer tracer) throws Exception {
    try (final StatefulRedisConnection<String,String> connection = client.connect()) {
      final RedisAsyncCommands<String,String> commands = connection.async();
      assertEquals("OK", commands.set("key2", "value2").get(15, TimeUnit.SECONDS));
      assertEquals("value2", commands.get("key2").get(15, TimeUnit.SECONDS));
    }

    final List<MockSpan> spans = tracer.finishedSpans();
    assertEquals(2, spans.size());
  }
}