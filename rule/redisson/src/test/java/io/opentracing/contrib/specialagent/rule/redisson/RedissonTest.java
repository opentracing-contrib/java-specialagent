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

package io.opentracing.contrib.specialagent.rule.redisson;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.List;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.redisson.Redisson;
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;

import io.opentracing.contrib.specialagent.AgentRunner;
import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;
import redis.embedded.RedisServer;

@RunWith(AgentRunner.class)
public class RedissonTest {
  private static RedisServer redisServer;

  @BeforeClass
  public static void beforeClass() throws IOException {
    startRedisServer();
  }

  @Before
  public void before(final MockTracer tracer) {
    tracer.reset();
  }

  @AfterClass
  public static void after() {
    if (redisServer != null)
      redisServer.stop();
  }

  @Test
  public void test(final MockTracer tracer) {
    final Config config = new Config();
    config.useSingleServer().setAddress("redis://127.0.0.1:6379");

    final RedissonClient redissonClient = Redisson.create(config);
    final RMap<String,String> map = redissonClient.getMap("map");

    map.put("key", "value");
    assertEquals("value", map.get("key"));

    final List<MockSpan> spans = tracer.finishedSpans();
    assertEquals(2, spans.size());

    redissonClient.shutdown();
  }

  private static void startRedisServer() throws IOException {
    redisServer = new RedisServer();
    final int maxAttempts = 10;
    for (int i = 1; i <= maxAttempts; i++) {
      try {
        redisServer.start();
        break;
      } catch (Exception e) {
        if(i == maxAttempts) {
          throw e;
        }
      }
    }
  }
}