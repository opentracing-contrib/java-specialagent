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

  private static void testRegexMatch(final String pluginName, final String expectedRegex, final String ... tests) {
    assertEquals(expectedRegex, SpecialAgentUtil.convertToNameRegex(pluginName));
    for (final String test : tests)
      assertTrue(test.matches(expectedRegex));
  }

  @Test
  public void testConvertToNameRegex() {
    try {
      SpecialAgentUtil.convertToNameRegex(null);
      fail("Expected NullPointerException");
    }
    catch (final NullPointerException e) {
    }

    try {
      SpecialAgentUtil.convertToNameRegex("");
      fail("Expected IllegalArgumentException");
    }
    catch (final IllegalArgumentException e) {
    }

    testRegexMatch("*", "^.*", "spring:webmvc",
      "okhttp", "lettuce", "jdbc");
    testRegexMatch("spring:*", "^spring:.*",
      "spring:webmvc", "spring:boot");
    testRegexMatch("spring:*:*", "^spring:[^:]*:.*",
      "spring:webmvc:3", "spring:webmvc:4", "spring:webmvc:5");
    testRegexMatch("spring:boot", "(^spring:boot|^spring:boot:.*)",
      "spring:boot");
    testRegexMatch("spring:webmvc", "(^spring:webmvc|^spring:webmvc:.*)",
      "spring:webmvc:3", "spring:webmvc:4", "spring:webmvc:5");

    testRegexMatch("lettuce:5.?", "^lettuce:5\\..",
      "lettuce:5.0", "lettuce:5.1", "lettuce:5.2");
    testRegexMatch("lettuce:5", "^lettuce:5.*",
      "lettuce:5", "lettuce:5.1", "lettuce:5.2");
    testRegexMatch("lettuce", "(^lettuce|^lettuce:.*)",
      "lettuce:5", "lettuce:5.0", "lettuce:5.1", "lettuce:5.2");
  }
}