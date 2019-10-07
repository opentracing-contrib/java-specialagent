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

import java.io.File;

import org.junit.Test;

import io.opentracing.contrib.specialagent.Manager.Event;

/**
 * Tests for methods in {@link SpecialAgentUtil}.
 *
 * @author Seva Safris
 */
public class SpecialAgentUtilTest {
  @Test
  public void testDigestEventsProperty() {
    Event[] events = SpecialAgentUtil.digestEventsProperty(null);
    for (int i = 0; i < events.length; ++i)
      assertNull(events[i]);

    events = SpecialAgentUtil.digestEventsProperty("DISCOVERY,TRANSFORMATION,IGNORED,ERROR,COMPLETE");
    for (final Event event : events)
      assertNotNull(event);

    events = SpecialAgentUtil.digestEventsProperty("");
    for (final Event event : events)
      assertNull(event);

    events = SpecialAgentUtil.digestEventsProperty("DISCOVERY");
    assertNotNull(events[Event.DISCOVERY.ordinal()]);
    assertNull(events[Event.COMPLETE.ordinal()]);
    assertNull(events[Event.TRANSFORMATION.ordinal()]);
    assertNull(events[Event.ERROR.ordinal()]);
    assertNull(events[Event.IGNORED.ordinal()]);

    events = SpecialAgentUtil.digestEventsProperty("TRANSFORMATION,COMPLETE");
    assertNotNull(events[Event.TRANSFORMATION.ordinal()]);
    assertNotNull(events[Event.COMPLETE.ordinal()]);
    assertNull(events[Event.DISCOVERY.ordinal()]);
    assertNull(events[Event.ERROR.ordinal()]);
    assertNull(events[Event.IGNORED.ordinal()]);
  }

  @Test
  public void testGetName() {
    try {
      SpecialAgentUtil.getName("");
      fail("Expected IllegalArgumentException");
    }
    catch (final IllegalArgumentException e) {
    }

    final String name = "opentracing-specialagent-1.3.3-SNAPSHOT.jar";
    final String s = File.separator;
    assertEquals(name, SpecialAgentUtil.getName(name));
    assertEquals(name, SpecialAgentUtil.getName("." + s + name));
    assertEquals(name, SpecialAgentUtil.getName("foo" + s + "bar" + s + name));
  }

  @Test
  public void testConvertToRegex() {
    try {
      SpecialAgentUtil.convertToRegex(null);
      fail("Expected NullPointerException");
    }
    catch (final NullPointerException e) {
    }

    try {
      SpecialAgentUtil.convertToRegex("");
      fail("Expected IllegalArgumentException");
    }
    catch (final IllegalArgumentException e) {
    }

    assertEquals(".*", SpecialAgentUtil.convertToRegex("*"));
    assertEquals("spring:.*", SpecialAgentUtil.convertToRegex("spring:*"));
    assertEquals("spring:[^:]*:.*", SpecialAgentUtil.convertToRegex("spring:*:*"));

    assertEquals("lettuce:5\\..", SpecialAgentUtil.convertToRegex("lettuce:5.?"));
  }
}