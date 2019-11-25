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

package io.opentracing.contrib.specialagent.test.jedis;

import io.opentracing.contrib.specialagent.TestUtil;
import redis.clients.jedis.Jedis;
import redis.embedded.RedisServer;

public class JedisITest {
  public static void main(final String[] args) throws Exception {
    TestUtil.initTerminalExceptionHandler();
    final RedisServer redisServer = new RedisServer();
    TestUtil.retry(new Runnable() {
      @Override
      public void run() {
        redisServer.start();
      }
    }, 10);

    Jedis jedis = new Jedis();
    if (!"OK".equals(jedis.set("key", "value")))
      throw new AssertionError("ERROR: failed to set key/value");

    if (!"value".equals(jedis.get("key")))
      throw new AssertionError("ERROR: failed to get key value");

    TestUtil.checkSpan("java-redis", 2);

    jedis.shutdown();
    redisServer.stop();
    // RedisServer process doesn't exit on 'stop' therefore call System.exit
    System.exit(0);
  }
}