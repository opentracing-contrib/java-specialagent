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

package io.opentracing.contrib.specialagent.lettuce;

import io.lettuce.core.api.async.RedisAsyncCommands;
import io.lettuce.core.cluster.api.async.RedisAdvancedClusterAsyncCommands;
import io.lettuce.core.pubsub.RedisPubSubListener;
import io.lettuce.core.pubsub.api.async.RedisPubSubAsyncCommands;
import io.opentracing.contrib.redis.common.TracingConfiguration;
import io.opentracing.contrib.redis.lettuce52.TracingRedisAdvancedClusterAsyncCommands;
import io.opentracing.contrib.redis.lettuce52.TracingRedisAsyncCommands;
import io.opentracing.contrib.redis.lettuce52.TracingRedisPubSubAsyncCommands;
import io.opentracing.contrib.redis.lettuce52.TracingRedisPubSubListener;
import io.opentracing.util.GlobalTracer;

public class Lettuce52AgentIntercept {
  private static Boolean IS_LETTUCE_52;

  private static boolean isLettuce52() {
    if (IS_LETTUCE_52 != null)
      return IS_LETTUCE_52;

    try {
      Class.forName("io.lettuce.core.XGroupCreateArgs");
      IS_LETTUCE_52 = true;
    } catch (ClassNotFoundException ignore) {
        IS_LETTUCE_52 = false;
    }

    return IS_LETTUCE_52;
  }

  @SuppressWarnings("unchecked")
  public static Object getAsyncCommands(final Object returned) {
    if (!isLettuce52())
      return returned;

    if (returned instanceof TracingRedisAsyncCommands)
      return returned;

    if (returned instanceof RedisPubSubAsyncCommands)
      return new TracingRedisPubSubAsyncCommands<>((RedisPubSubAsyncCommands<Object,Object>)returned, new TracingConfiguration.Builder(GlobalTracer.get()).build());

    return new TracingRedisAsyncCommands<>((RedisAsyncCommands<Object,Object>)returned, new TracingConfiguration.Builder(GlobalTracer.get()).build());
  }

  @SuppressWarnings("unchecked")
  public static Object getAsyncClusterCommands(final Object returned) {
    if (!isLettuce52())
      return returned;

    if (returned instanceof TracingRedisAdvancedClusterAsyncCommands)
      return returned;

    return new TracingRedisAdvancedClusterAsyncCommands<>((RedisAdvancedClusterAsyncCommands<Object,Object>)returned, new TracingConfiguration.Builder(GlobalTracer.get()).build());
  }

  @SuppressWarnings("unchecked")
  public static Object addPubSubListener(final Object arg) {
    if (!isLettuce52())
      return arg;

    if (arg instanceof TracingRedisPubSubListener)
      return arg;

    return new TracingRedisPubSubListener<>((RedisPubSubListener<Object,Object>)arg, new TracingConfiguration.Builder(GlobalTracer.get()).build());
  }
}