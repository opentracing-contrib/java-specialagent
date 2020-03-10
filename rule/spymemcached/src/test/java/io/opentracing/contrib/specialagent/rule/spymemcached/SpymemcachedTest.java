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

package io.opentracing.contrib.specialagent.rule.spymemcached;

import static junit.framework.TestCase.*;

import java.net.InetSocketAddress;

import org.junit.Test;
import org.junit.runner.RunWith;

import io.opentracing.contrib.specialagent.AgentRunner;
import io.opentracing.mock.MockTracer;
import net.spy.memcached.MemcachedClient;

@RunWith(AgentRunner.class)
public class SpymemcachedTest {
  @Test
  public void test(final MockTracer tracer) throws Exception {
    final MemcachedClient client = new MemcachedClient(new InetSocketAddress("localhost", 11211));

    try {
      client.set("key", 2, 2).get();
    }
    catch (final Exception ignore) {
    }

    try {
      client.get("key");
    }
    catch (final Exception ignore) {
    }

    assertEquals(2, tracer.finishedSpans().size());
  }
}