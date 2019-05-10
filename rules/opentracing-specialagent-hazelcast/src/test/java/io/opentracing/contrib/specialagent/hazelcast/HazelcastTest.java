package io.opentracing.contrib.specialagent.hazelcast;

import static org.junit.Assert.assertEquals;

import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;
import io.opentracing.contrib.specialagent.AgentRunner;
import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;
import java.util.List;
import java.util.Set;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AgentRunner.class)
@AgentRunner.Config(isolateClassLoader = false)
public class HazelcastTest {
  private static HazelcastInstance hazelcast;

  @BeforeClass
  public static void beforeClass() {
    Config config = new Config();
    config.setInstanceName("name");
    hazelcast = Hazelcast.newHazelcastInstance(config);
  }

  @AfterClass
  public static void afterClass() {
    if (hazelcast != null) {
      hazelcast.shutdown();
    }
  }

  @Before
  public void before(MockTracer tracer) {
    tracer.reset();
  }

  @Test
  public void testNewHazelcastInstance(MockTracer tracer) {
    test(hazelcast, tracer);
  }

  @Test
  public void testInstanceByName(MockTracer tracer) {
    test(Hazelcast.getHazelcastInstanceByName("name"), tracer);
  }

  @Test
  public void testGetOrCreateHazelcastInstance(MockTracer tracer) {
    Config config = new Config();
    config.setInstanceName("name");
    test(Hazelcast.getOrCreateHazelcastInstance(config), tracer);
  }

  @Test
  public void testGetAllHazelcastInstances(MockTracer tracer) {
    Set<HazelcastInstance> instances = Hazelcast.getAllHazelcastInstances();
    test(instances.iterator().next(), tracer);
  }

  private void test(HazelcastInstance instance, MockTracer tracer) {
    IMap<String, String> map = instance.getMap("map");
    map.put("key", "value");
    assertEquals("value", map.get("key"));
    List<MockSpan> spans = tracer.finishedSpans();
    assertEquals(2, spans.size());
  }
}
