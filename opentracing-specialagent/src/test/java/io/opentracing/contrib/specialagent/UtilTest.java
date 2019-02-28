/* Copyright 2018 The OpenTracing Authors
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

package io.opentracing.contrib.specialagent;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import org.junit.Test;

import io.opentracing.Tracer;
import io.opentracing.contrib.specialagent.Manager.Event;
import io.opentracing.noop.NoopTracer;

/**
 * Tests for methods in {@link Util}.
 *
 * @author Seva Safris
 */
public class UtilTest {
  @Test
  public void testGetPluginPathsAll() throws IOException {
    final Set<String> expected = new HashSet<>();
    expected.add("bcprov-jdk15on-1.50.jar");
    expected.add("hamcrest-core-1.3.jar");
    expected.add("junit-4.12.jar");
    expected.add("mockwebserver-3.6.0.jar");
    expected.add("okhttp-3.6.0.jar");
    expected.add("okio-1.11.0.jar");
    expected.add("opentracing-api-0.31.0.jar");
    expected.add("opentracing-concurrent-0.1.0.jar");
    expected.add("opentracing-mock-0.31.0.jar");
    expected.add("opentracing-noop-0.31.0.jar");
    expected.add("opentracing-okhttp3-0.1.0.jar");
    expected.add("opentracing-specialagent-okhttp-0.0.0-SNAPSHOT.jar");
    expected.add("opentracing-specialagent1-0.0.0-SNAPSHOT-tests.jar");
    expected.add("opentracing-specialagent2-0.0.0-SNAPSHOT-tests.jar");
    expected.add("opentracing-util-0.31.0.jar");

    final String test = new String(Util.readBytes(Thread.currentThread().getContextClassLoader().getResource("test.tgf")));
    final Set<String> actual = Util.selectFromTgf(test, true, null);
    assertEquals(expected, actual);
  }

  @Test
  public void testGetPluginPathsOptionalTest() throws IOException {
    final Set<String> expected = new HashSet<>();
    expected.add("bcprov-jdk15on-1.50.jar");
    expected.add("hamcrest-core-1.3.jar");
    expected.add("junit-4.12.jar");
    expected.add("mockwebserver-3.6.0.jar");
    expected.add("okhttp-3.6.0.jar");
    expected.add("okio-1.11.0.jar");
    expected.add("opentracing-mock-0.31.0.jar");
    expected.add("opentracing-specialagent1-0.0.0-SNAPSHOT-tests.jar");
    expected.add("opentracing-util-0.31.0.jar");

    final String test = new String(Util.readBytes(Thread.currentThread().getContextClassLoader().getResource("test.tgf")));
    final Set<String> actual = Util.selectFromTgf(test, true, new String[] {"test"});
    assertEquals(expected, actual);
  }

  @Test
  public void testGetPluginPathsTest() throws IOException {
    final Set<String> expected = new HashSet<>();
    expected.add("bcprov-jdk15on-1.50.jar");
    expected.add("hamcrest-core-1.3.jar");
    expected.add("junit-4.12.jar");
    expected.add("mockwebserver-3.6.0.jar");
    expected.add("opentracing-mock-0.31.0.jar");
    expected.add("opentracing-specialagent1-0.0.0-SNAPSHOT-tests.jar");
    expected.add("opentracing-util-0.31.0.jar");

    final String test = new String(Util.readBytes(Thread.currentThread().getContextClassLoader().getResource("test.tgf")));
    final Set<String> actual = Util.selectFromTgf(test, false, new String[] {"test"});
    assertEquals(expected, actual);
  }

  @Test
  public void testGetPluginPathsCompile() throws IOException {
    final Set<String> expected = new HashSet<>();
    expected.add("opentracing-concurrent-0.1.0.jar");
    expected.add("opentracing-okhttp3-0.1.0.jar");
    expected.add("opentracing-specialagent-okhttp-0.0.0-SNAPSHOT.jar");
    expected.add("opentracing-specialagent2-0.0.0-SNAPSHOT-tests.jar");
    expected.add("opentracing-noop-0.31.0.jar");
    expected.add("opentracing-api-0.31.0.jar");

    final String test = new String(Util.readBytes(Thread.currentThread().getContextClassLoader().getResource("test.tgf")));
    final Set<String> actual = Util.selectFromTgf(test, false, new String[] {"compile"});
    assertEquals(expected, actual);
  }

  @Test
  public void testGetPluginPathsCompileExclude() throws IOException {
    final Set<String> expected = new HashSet<>();
    expected.add("opentracing-concurrent-0.1.0.jar");
    expected.add("opentracing-okhttp3-0.1.0.jar");
    expected.add("opentracing-specialagent-okhttp-0.0.0-SNAPSHOT.jar");
    expected.add("opentracing-specialagent2-0.0.0-SNAPSHOT-tests.jar");

    final String test = new String(Util.readBytes(Thread.currentThread().getContextClassLoader().getResource("test.tgf")));
    final Set<String> actual = Util.selectFromTgf(test, false, new String[] {"compile"}, NoopTracer.class, Tracer.class);
    assertEquals(expected, actual);
  }

  @Test
  public void testRetain() {
    String[] a, b, r;

    a = new String[] {"a", "b", "c", "d"};
    b = new String[] {"a", "b", "c", "d"};
    r = Util.retain(a, b, 0, 0, 0);
    assertArrayEquals(a, r);

    a = new String[] {"b", "c", "d"};
    b = new String[] {"a", "b", "c", "d"};
    r = Util.retain(a, b, 0, 0, 0);
    assertArrayEquals(a, r);

    a = new String[] {"a", "b", "c", "d"};
    b = new String[] {"b", "c", "d"};
    r = Util.retain(a, b, 0, 0, 0);
    assertArrayEquals(b, r);

    a = new String[] {"a", "b", "c"};
    b = new String[] {"a", "b", "c", "d"};
    r = Util.retain(a, b, 0, 0, 0);
    assertArrayEquals(a, r);

    a = new String[] {"a", "b", "c", "d"};
    b = new String[] {"a", "b", "c"};
    r = Util.retain(a, b, 0, 0, 0);
    assertArrayEquals(b, r);

    a = new String[] {"a", "b", "d"};
    b = new String[] {"a", "b", "c", "d"};
    r = Util.retain(a, b, 0, 0, 0);
    assertArrayEquals(a, r);

    a = new String[] {"a", "b", "c", "d"};
    b = new String[] {"a", "b", "d"};
    r = Util.retain(a, b, 0, 0, 0);
    assertArrayEquals(b, r);

    a = new String[] {"a", "c", "d"};
    b = new String[] {"a", "b", "c", "d"};
    r = Util.retain(a, b, 0, 0, 0);
    assertArrayEquals(a, r);

    a = new String[] {"a", "b", "c", "d"};
    b = new String[] {"a", "c", "d"};
    r = Util.retain(a, b, 0, 0, 0);
    assertArrayEquals(b, r);

    a = new String[] {"a", "d"};
    b = new String[] {"a", "b", "c", "d"};
    r = Util.retain(a, b, 0, 0, 0);
    assertArrayEquals(a, r);

    a = new String[] {"a", "b", "c", "d"};
    b = new String[] {"a", "d"};
    r = Util.retain(a, b, 0, 0, 0);
    assertArrayEquals(b, r);

    a = new String[] {"a", "c", "d"};
    b = new String[] {"a", "b", "d"};
    r = Util.retain(a, b, 0, 0, 0);
    assertArrayEquals(new String[] {"a", "d"}, r);

    a = new String[] {"a", "b", "d"};
    b = new String[] {"a", "c", "d"};
    r = Util.retain(a, b, 0, 0, 0);
    assertArrayEquals(new String[] {"a", "d"}, r);

    a = new String[] {"c", "d"};
    b = new String[] {"a", "b", "d"};
    r = Util.retain(a, b, 0, 0, 0);
    assertArrayEquals(new String[] {"d"}, r);

    a = new String[] {"a", "b"};
    b = new String[] {"a", "c"};
    r = Util.retain(a, b, 0, 0, 0);
    assertArrayEquals(new String[] {"a"}, r);

    a = new String[] {"a", "b"};
    b = new String[] {"c", "d"};
    r = Util.retain(a, b, 0, 0, 0);
    assertNull(r);
  }

  @Test
  public void testContainsAll() {
    String[] a, b;

    a = new String[] {"a", "b", "c", "d"};
    b = new String[] {"a", "b", "c", "d"};
    assertTrue(Util.containsAll(a, b));

    a = new String[] {"b", "c", "d"};
    b = new String[] {"a", "b", "c", "d"};
    assertFalse(Util.containsAll(a, b));

    a = new String[] {"a", "b", "c", "d"};
    b = new String[] {"b", "c", "d"};
    assertTrue(Util.containsAll(a, b));

    a = new String[] {"a", "b", "c"};
    b = new String[] {"a", "b", "c", "d"};
    assertFalse(Util.containsAll(a, b));

    a = new String[] {"a", "b", "c", "d"};
    b = new String[] {"a", "b", "c"};
    assertTrue(Util.containsAll(a, b));

    a = new String[] {"a", "b", "d"};
    b = new String[] {"a", "b", "c", "d"};
    assertFalse(Util.containsAll(a, b));

    a = new String[] {"a", "b", "c", "d"};
    b = new String[] {"a", "b", "d"};
    assertTrue(Util.containsAll(a, b));

    a = new String[] {"a", "c", "d"};
    b = new String[] {"a", "b", "c", "d"};
    assertFalse(Util.containsAll(a, b));

    a = new String[] {"a", "b", "c", "d"};
    b = new String[] {"a", "c", "d"};
    assertTrue(Util.containsAll(a, b));

    a = new String[] {"a", "d"};
    b = new String[] {"a", "b", "c", "d"};
    assertFalse(Util.containsAll(a, b));

    a = new String[] {"a", "b", "c", "d"};
    b = new String[] {"a", "d"};
    assertTrue(Util.containsAll(a, b));

    a = new String[] {"a", "c", "d"};
    b = new String[] {"a", "b", "d"};
    assertFalse(Util.containsAll(a, b));

    a = new String[] {"a", "b", "d"};
    b = new String[] {"a", "c", "d"};
    assertFalse(Util.containsAll(a, b));

    a = new String[] {"c", "d"};
    b = new String[] {"a", "b", "d"};
    assertFalse(Util.containsAll(a, b));

    a = new String[] {"a", "b"};
    b = new String[] {"a", "c"};
    assertFalse(Util.containsAll(a, b));

    a = new String[] {"a", "b"};
    b = new String[] {"c", "d"};
    assertFalse(Util.containsAll(a, b));
  }

  @Test
  public void testDigestEventsProperty() {
    Event[] events = Util.digestEventsProperty(null);
    for (final Event event : events)
      assertNull(event);

    events = Util.digestEventsProperty("DISCOVERY,TRANSFORMATION,IGNORED,ERROR,COMPLETE");
    for (final Event event : events)
      assertNotNull(event);

    events = Util.digestEventsProperty("");
    for (final Event event : events)
      assertNull(event);

    events = Util.digestEventsProperty("DISCOVERY");
    assertNotNull(events[Event.DISCOVERY.ordinal()]);
    assertNull(events[Event.COMPLETE.ordinal()]);
    assertNull(events[Event.TRANSFORMATION.ordinal()]);
    assertNull(events[Event.ERROR.ordinal()]);
    assertNull(events[Event.IGNORED.ordinal()]);

    events = Util.digestEventsProperty("TRANSFORMATION,COMPLETE");
    assertNotNull(events[Event.TRANSFORMATION.ordinal()]);
    assertNotNull(events[Event.COMPLETE.ordinal()]);
    assertNull(events[Event.DISCOVERY.ordinal()]);
    assertNull(events[Event.ERROR.ordinal()]);
    assertNull(events[Event.IGNORED.ordinal()]);
  }
}