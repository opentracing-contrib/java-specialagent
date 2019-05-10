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

package io.opentracing.contrib.specialagent.hazelcast;

import java.util.HashSet;
import java.util.Set;

import com.hazelcast.core.HazelcastInstance;

import io.opentracing.contrib.hazelcast.TracingHazelcastInstance;

public class HazelcastAgentIntercept {
  public static Object exit(final Object returned) {
    return new TracingHazelcastInstance((HazelcastInstance)returned, false);
  }

  @SuppressWarnings("unchecked")
  public static Object getAllHazelcastInstances(final Object returned) {
    if (returned == null)
      return null;

    final Set<HazelcastInstance> instances = (Set<HazelcastInstance>)returned;
    final Set<HazelcastInstance> tracingInstances = new HashSet<>();
    for (final HazelcastInstance instance : instances)
      tracingInstances.add(new TracingHazelcastInstance(instance, false));

    return tracingInstances;
  }
}