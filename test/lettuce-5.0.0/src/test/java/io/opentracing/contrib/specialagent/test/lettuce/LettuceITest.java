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

package io.opentracing.contrib.specialagent.test.lettuce;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import io.opentracing.contrib.specialagent.TestUtil;
import java.lang.reflect.Method;
import redis.embedded.RedisServer;

public class LettuceITest {
  public static void main(final String[] args) throws Exception {
    TestUtil.initTerminalExceptionHandler();
    RedisServer redisServer = new RedisServer();
    TestUtil.retry(() -> redisServer.start(), 10);

    RedisClient client = RedisClient.create("redis://localhost");
    StatefulRedisConnection<String, String> connection = client.connect();
    RedisCommands<String, String> commands = connection.sync();
    if (!"OK".equals(commands.set("key", "value"))) {
      throw new AssertionError("ERROR: failed to set key/value");
    }
    if (!"value".equals(commands.get("key"))) {
      throw new AssertionError("ERROR: failed to get key value");
    }

    int spanCount = 2;

    // Lettuce 5.1+ has new method 'xlen'
    final Method xlenMethod = getMethod(commands, "xlen");
    if (xlenMethod != null) {
      spanCount++;
      try {
        xlenMethod.invoke(commands, "key");
      } catch (Exception ignore) {
        // embedded server doesn't support all commands
      }
    }

    // Lettuce 5.2+ has new method 'memoryUsage'
    final Method memoryUsageMethod = getMethod(commands, "memoryUsage");
    if (memoryUsageMethod != null) {
      spanCount++;
      try {
        memoryUsageMethod.invoke(commands, "key");
      } catch (Exception ignore) {
        // embedded server doesn't support all commands
      }
    }

    client.shutdown();
    redisServer.stop();
    TestUtil.checkSpan("java-redis", spanCount);

    // RedisServer process doesn't exit on 'stop' therefore call System.exit
    System.exit(0);
  }

  private static Method getMethod(RedisCommands<String, String> commands, String name) {
    for (Method method : commands.getClass().getMethods()) {
      if (method.getName().equals(name)) {
        return method;
      }
    }
    return null;
  }

}