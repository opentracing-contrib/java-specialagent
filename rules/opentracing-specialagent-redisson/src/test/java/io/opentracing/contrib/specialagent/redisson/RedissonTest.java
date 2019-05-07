package io.opentracing.contrib.specialagent.redisson;

import static org.junit.Assert.assertEquals;

import io.opentracing.contrib.specialagent.AgentRunner;
import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;
import java.io.IOException;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.redisson.Redisson;
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import redis.embedded.RedisServer;

@RunWith(AgentRunner.class)
public class RedissonTest {
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
    Config config = new Config();
    config.useSingleServer().setAddress("redis://127.0.0.1:6379");

    RedissonClient redissonClient = Redisson.create(config);

    RMap<String, String> map = redissonClient.getMap("map");

    map.put("key", "value");
    assertEquals("value", map.get("key"));

    List<MockSpan> spans = tracer.finishedSpans();
    assertEquals(2, spans.size());

    redissonClient.shutdown();
  }

}
