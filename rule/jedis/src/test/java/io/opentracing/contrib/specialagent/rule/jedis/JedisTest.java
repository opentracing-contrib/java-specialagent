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

package io.opentracing.contrib.specialagent.rule.jedis;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.List;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.opentracing.contrib.specialagent.AgentRunner;
import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;
import io.opentracing.tag.Tags;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.Pipeline;
import redis.embedded.RedisServer;

@RunWith(AgentRunner.class)
public class JedisTest {
  private static RedisServer redisServer;
  private Jedis jedis;

  @BeforeClass
  public static void beforeClass() throws IOException {
    redisServer = new RedisServer();
    redisServer.start();
  }

  @AfterClass
  public static void afterClass() {
    if (redisServer != null)
      redisServer.stop();
  }

  @Before
  public void before(final MockTracer tracer) {
    tracer.reset();
    jedis = new Jedis();
  }

  @After
  public void after() {
    if (jedis != null)
      jedis.close();
  }

  @Test
  public void test(final MockTracer tracer) {
    assertEquals("OK", jedis.set("key", "value"));
    assertEquals("value", jedis.get("key"));

    final List<MockSpan> spans = tracer.finishedSpans();
    assertEquals(2, spans.size());
    checkSpans(spans);
  }

  @Test
  public void withError(final MockTracer tracer) {
    try {
      jedis.eval("bla-bla-bla");
      fail();
    }
    catch (final Exception ignore) {
    }

    final List<MockSpan> spans = tracer.finishedSpans();
    assertEquals(1, spans.size());
    assertEquals(Boolean.TRUE, spans.get(0).tags().get(Tags.ERROR.getKey()));
    assertFalse(spans.get(0).logEntries().isEmpty());
    checkSpans(spans);
  }

  @Test
  public void pipeline(final MockTracer tracer) {
    final Pipeline pipeline = jedis.pipelined();
    for (int i = 0; i < 5; ++i)
      pipeline.set("key-" + i, "value-" + i);

    pipeline.sync();
    final List<MockSpan> spans = tracer.finishedSpans();
    assertEquals(5, spans.size());
    checkSpans(spans);
  }

  private static void checkSpans(final List<MockSpan> spans) {
    for (final MockSpan span : spans) {
      assertEquals("java-redis", span.tags().get(Tags.COMPONENT.getKey()));
      assertEquals("redis", span.tags().get(Tags.DB_TYPE.getKey()));
      assertEquals(Tags.SPAN_KIND_CLIENT, span.tags().get(Tags.SPAN_KIND.getKey()));
    }
  }
}