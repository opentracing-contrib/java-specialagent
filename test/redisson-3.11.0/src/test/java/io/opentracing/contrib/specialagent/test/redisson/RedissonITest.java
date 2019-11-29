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

package io.opentracing.contrib.specialagent.test.redisson;

import io.opentracing.contrib.specialagent.TestUtil;
import org.redisson.Redisson;
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import redis.embedded.RedisServer;

public class RedissonITest {
  public static void main(final String[] args) throws Exception {
    TestUtil.initTerminalExceptionHandler();
    final RedisServer redisServer = new RedisServer();
    TestUtil.retry(new Runnable() {
      @Override
      public void run() {
        redisServer.start();
      }
    }, 10);

    final Config config = new Config();
    config.useSingleServer().setAddress("redis://127.0.0.1:6379");
    final RedissonClient redissonClient = Redisson.create(config);
    final RMap<String,String> map = redissonClient.getMap("map");

    map.put("key", "value");

    if (!"value".equals(map.get("key")))
      throw new AssertionError("ERROR: failed to get key value");

    redissonClient.shutdown();
    redisServer.stop();

    TestUtil.checkSpan("java-redis", 2);

    // RedisServer process doesn't exit on 'stop' therefore call System.exit
    System.exit(0);
  }
}