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

package io.opentracing.contrib.specialagent.rule.lettuce50;

import io.lettuce.core.api.async.RedisAsyncCommands;
import io.lettuce.core.cluster.api.async.RedisAdvancedClusterAsyncCommands;
import io.lettuce.core.pubsub.RedisPubSubListener;
import io.lettuce.core.pubsub.api.async.RedisPubSubAsyncCommands;
import io.opentracing.contrib.redis.common.TracingConfiguration;
import io.opentracing.contrib.redis.lettuce50.TracingRedisAdvancedClusterAsyncCommands;
import io.opentracing.contrib.redis.lettuce50.TracingRedisAsyncCommands;
import io.opentracing.contrib.redis.lettuce50.TracingRedisPubSubAsyncCommands;
import io.opentracing.contrib.redis.lettuce50.TracingRedisPubSubListener;
import io.opentracing.util.GlobalTracer;

public class Lettuce50AgentIntercept {
  public static Object getAsyncCommands(final Object returned) {
    if (returned instanceof RedisPubSubAsyncCommands)
      return new TracingRedisPubSubAsyncCommands<>((RedisPubSubAsyncCommands<?,?>)returned, new TracingConfiguration.Builder(GlobalTracer.get()).build());

    return new TracingRedisAsyncCommands<>((RedisAsyncCommands<?,?>)returned, new TracingConfiguration.Builder(GlobalTracer.get()).build());
  }

  public static Object getAsyncClusterCommands(final Object returned) {
    return new TracingRedisAdvancedClusterAsyncCommands<>((RedisAdvancedClusterAsyncCommands<?,?>)returned, new TracingConfiguration.Builder(GlobalTracer.get()).build());
  }

  public static Object addPubSubListener(final Object arg) {
    return new TracingRedisPubSubListener<>((RedisPubSubListener<?,?>)arg, new TracingConfiguration.Builder(GlobalTracer.get()).build());
  }
}