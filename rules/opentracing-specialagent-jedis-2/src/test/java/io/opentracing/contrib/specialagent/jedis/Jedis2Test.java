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
package io.opentracing.contrib.specialagent.jedis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

import io.opentracing.contrib.specialagent.AgentRunner;
import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;
import io.opentracing.tag.Tags;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.Pipeline;
import redis.embedded.RedisServer;

@RunWith(AgentRunner.class)
public class Jedis2Test {
  private RedisServer redisServer;
  private Jedis jedis;

  @Before
  public void before(final MockTracer tracer) {
    tracer.reset();

    redisServer = RedisServer.builder().setting("bind 127.0.0.1").build();
    redisServer.start();
    jedis = new Jedis();
  }

  @After
  public void after() {
    if (redisServer != null) {
      redisServer.stop();
    }
    if (jedis != null) {
      jedis.close();
    }
  }

  @Test
  public void test(MockTracer tracer) {
    assertEquals("OK", jedis.set("key", "value"));
    assertEquals("value", jedis.get("key"));

    List<MockSpan> spans = tracer.finishedSpans();
    assertEquals(2, spans.size());
    checkSpans(spans);
  }

  @Test
  public void withError(MockTracer tracer) {
    try {
      jedis.eval("bla-bla-bla");
      fail();
    } catch (Exception ignore) {
    }

    List<MockSpan> spans = tracer.finishedSpans();
    assertEquals(1, spans.size());
    assertEquals(Boolean.TRUE, spans.get(0).tags().get(Tags.ERROR.getKey()));
    assertFalse(spans.get(0).logEntries().isEmpty());
    checkSpans(spans);
  }

  @Test
  public void pipeline(MockTracer tracer) {
    Pipeline p = jedis.pipelined();
    for (int i = 0; i < 5; i++) {
      p.set("key-" + i, "value-" + i);
    }
    p.sync();
    List<MockSpan> spans = tracer.finishedSpans();
    assertEquals(5, spans.size());
    checkSpans(spans);
  }

  private void checkSpans(List<MockSpan> spans) {
    for (MockSpan span : spans) {
      assertEquals("java-redis", span.tags().get(Tags.COMPONENT.getKey()));
      assertEquals("redis", span.tags().get(Tags.DB_TYPE.getKey()));
      assertEquals(Tags.SPAN_KIND_CLIENT, span.tags().get(Tags.SPAN_KIND.getKey()));
    }
  }
}
