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

import static org.junit.Assert.*;

import java.util.List;
import java.util.Set;

import com.hazelcast.client.HazelcastClient;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;

import io.opentracing.contrib.specialagent.AgentRunner;
import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;

@RunWith(AgentRunner.class)
@AgentRunner.Config(isolateClassLoader = false)
public class HazelcastTest {
  private static HazelcastInstance hazelcast;

  @BeforeClass
  public static void beforeClass() {
    final Config config = new Config();
    config.setInstanceName("name");
    hazelcast = Hazelcast.newHazelcastInstance(config);
  }

  @AfterClass
  public static void afterClass() {
    hazelcast.shutdown();
  }

  @Before
  public void before(final MockTracer tracer) {
    tracer.reset();
  }

  @Test
  public void testNewHazelcastInstance(final MockTracer tracer) {
    test(hazelcast, tracer);
  }

  @Test
  public void testInstanceByName(final MockTracer tracer) {
    test(Hazelcast.getHazelcastInstanceByName("name"), tracer);
  }

  @Test
  public void testGetOrCreateHazelcastInstance(final MockTracer tracer) {
    final Config config = new Config();
    config.setInstanceName("name");
    test(Hazelcast.getOrCreateHazelcastInstance(config), tracer);
  }

  @Test
  public void testGetAllHazelcastInstances(final MockTracer tracer) {
    final Set<HazelcastInstance> instances = Hazelcast.getAllHazelcastInstances();
    test(instances.iterator().next(), tracer);
  }

  @Test
  public void testNewHazelcastClientInstance(final MockTracer tracer) {
    HazelcastInstance hazelcast2 = HazelcastClient.newHazelcastClient();
    test(hazelcast2, tracer);
  }

  private static void test(final HazelcastInstance instance, final MockTracer tracer) {
    final IMap<String,String> map = instance.getMap("map");
    map.put("key", "value");
    assertEquals("value", map.get("key"));
    final List<MockSpan> spans = tracer.finishedSpans();
    assertEquals(2, spans.size());
  }
}