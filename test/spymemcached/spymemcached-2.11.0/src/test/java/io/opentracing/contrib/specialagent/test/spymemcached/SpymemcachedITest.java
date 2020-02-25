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

package io.opentracing.contrib.specialagent.test.spymemcached;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

import io.opentracing.contrib.specialagent.TestUtil;
import io.opentracing.contrib.specialagent.TestUtil.ComponentSpanCount;
import net.spy.memcached.MemcachedClient;

public class SpymemcachedITest {
  public static void main(final String[] args) throws Exception {
    final MemcachedClient client = new MemcachedClient(new InetSocketAddress("localhost", 11211));
    final boolean op = client.set("key", 120, "value").get(15, TimeUnit.SECONDS);
    if (!op)
      throw new AssertionError("Failed to set value");

    if (!"value".equals(client.get("key")))
      throw new AssertionError("Failed to get value");

    client.shutdown();
    TestUtil.checkSpan(new ComponentSpanCount("java-memcached", 2));
  }
}