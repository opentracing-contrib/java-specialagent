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
import io.opentracing.contrib.redis.common.TracingConfiguration;
import io.opentracing.contrib.redis.lettuce.TracingRedisAdvancedClusterAsyncCommands;
import io.opentracing.contrib.redis.lettuce.TracingRedisAsyncCommands;
import io.opentracing.util.GlobalTracer;

public class LettuceAgentIntercept {
  @SuppressWarnings("unchecked")
  public static Object getAsyncCommands(final Object returned) {
    if (returned instanceof TracingRedisAsyncCommands)
      return returned;

    return new TracingRedisAsyncCommands<>((RedisAsyncCommands<Object,Object>)returned, new TracingConfiguration.Builder(GlobalTracer.get()).build());
  }

  @SuppressWarnings("unchecked")
  public static Object getAsyncClusterCommands(final Object returned) {
    if (returned instanceof TracingRedisAdvancedClusterAsyncCommands)
      return returned;

    return new TracingRedisAdvancedClusterAsyncCommands<>((RedisAdvancedClusterAsyncCommands<Object,Object>)returned, new TracingConfiguration.Builder(GlobalTracer.get()).build());
  }
}