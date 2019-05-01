package io.opentracing.contrib.specialagent.lettuce;

import static org.junit.Assert.assertEquals;

import io.lettuce.core.ConnectionFuture;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.codec.StringCodec;
import io.opentracing.contrib.specialagent.AgentRunner;
import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import redis.embedded.RedisServer;

@RunWith(AgentRunner.class)
public class LettuceTest {
  private RedisServer redisServer;

  @Before
  public void before(final MockTracer tracer) throws IOException {
    tracer.reset();

    redisServer = new RedisServer();
    redisServer.start();
  }

  @After
  public void after() {
    if (redisServer != null) {
      redisServer.stop();
    }
  }

  @Test
  public void test(final MockTracer tracer) {
    RedisClient client = RedisClient.create("redis://localhost");
    StatefulRedisConnection<String, String> connection = client.connect();
    RedisCommands<String, String> commands = connection.sync();
    assertEquals("OK", commands.set("key", "value"));
    assertEquals("value", commands.get("key"));

    connection.close();

    client.shutdown();

    List<MockSpan> spans = tracer.finishedSpans();
    assertEquals(2, spans.size());
  }

  @Test
  public void testConnectAsync(final MockTracer tracer) throws Exception {
    RedisClient client = RedisClient.create("redis://localhost");
    ConnectionFuture<StatefulRedisConnection<String, String>> connectionFuture = client
        .connectAsync(StringCodec.UTF8, RedisURI.create("redis://localhost"));

    final StatefulRedisConnection<String, String> connection = connectionFuture
        .get(10, TimeUnit.SECONDS);

    RedisCommands<String, String> commands = connection.sync();
    assertEquals("OK", commands.set("key", "value"));
    assertEquals("value", commands.get("key"));

    connection.close();

    client.shutdown();

    List<MockSpan> spans = tracer.finishedSpans();
    assertEquals(2, spans.size());
  }

  @Test
  public void async(MockTracer tracer) throws Exception {
    RedisClient client = RedisClient.create("redis://localhost");

    StatefulRedisConnection<String, String> connection = client.connect();

    RedisAsyncCommands<String, String> commands = connection.async();

    assertEquals("OK", commands.set("key2", "value2").get(15, TimeUnit.SECONDS));

    assertEquals("value2", commands.get("key2").get(15, TimeUnit.SECONDS));

    connection.close();

    client.shutdown();

    List<MockSpan> spans = tracer.finishedSpans();
    assertEquals(2, spans.size());
  }
}
