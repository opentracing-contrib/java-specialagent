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

package io.opentracing.contrib.specialagent.test.hazelcast;

import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;

import io.opentracing.contrib.specialagent.TestUtil;

public class HazelcastITest {
  public static void main(final String[] args) {
    final HazelcastInstance instance = Hazelcast.newHazelcastInstance(new Config().setProperty("hazelcast.phone.home.enabled", "false"));
    final IMap<String,String> map = instance.getMap("map");
    map.put("key", "value");
    if (!"value".equals(map.get("key")))
      throw new AssertionError("ERROR: wrong value");

    instance.shutdown();

    TestUtil.checkSpan("java-hazelcast", 2);
  }
}