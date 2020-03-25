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

package io.opentracing.contrib.specialagent;

import static org.junit.Assert.*;

import java.io.File;

import org.junit.Test;

/**
 * Tests for methods in {@link AssembleUtil}.
 *
 * @author Seva Safris
 */
public class AssembleUtilTest {
  @Test
  public void testRetain() {
    String[] a, b, r;

    a = new String[] {"a", "b", "c", "d"};
    b = new String[] {"a", "b", "c", "d"};
    r = AssembleUtil.retain(a, b, 0, 0, 0);
    assertArrayEquals(a, r);

    a = new String[] {"b", "c", "d"};
    b = new String[] {"a", "b", "c", "d"};
    r = AssembleUtil.retain(a, b, 0, 0, 0);
    assertArrayEquals(a, r);

    a = new String[] {"a", "b", "c", "d"};
    b = new String[] {"b", "c", "d"};
    r = AssembleUtil.retain(a, b, 0, 0, 0);
    assertArrayEquals(b, r);

    a = new String[] {"a", "b", "c"};
    b = new String[] {"a", "b", "c", "d"};
    r = AssembleUtil.retain(a, b, 0, 0, 0);
    assertArrayEquals(a, r);

    a = new String[] {"a", "b", "c", "d"};
    b = new String[] {"a", "b", "c"};
    r = AssembleUtil.retain(a, b, 0, 0, 0);
    assertArrayEquals(b, r);

    a = new String[] {"a", "b", "d"};
    b = new String[] {"a", "b", "c", "d"};
    r = AssembleUtil.retain(a, b, 0, 0, 0);
    assertArrayEquals(a, r);

    a = new String[] {"a", "b", "c", "d"};
    b = new String[] {"a", "b", "d"};
    r = AssembleUtil.retain(a, b, 0, 0, 0);
    assertArrayEquals(b, r);

    a = new String[] {"a", "c", "d"};
    b = new String[] {"a", "b", "c", "d"};
    r = AssembleUtil.retain(a, b, 0, 0, 0);
    assertArrayEquals(a, r);

    a = new String[] {"a", "b", "c", "d"};
    b = new String[] {"a", "c", "d"};
    r = AssembleUtil.retain(a, b, 0, 0, 0);
    assertArrayEquals(b, r);

    a = new String[] {"a", "d"};
    b = new String[] {"a", "b", "c", "d"};
    r = AssembleUtil.retain(a, b, 0, 0, 0);
    assertArrayEquals(a, r);

    a = new String[] {"a", "b", "c", "d"};
    b = new String[] {"a", "d"};
    r = AssembleUtil.retain(a, b, 0, 0, 0);
    assertArrayEquals(b, r);

    a = new String[] {"a", "c", "d"};
    b = new String[] {"a", "b", "d"};
    r = AssembleUtil.retain(a, b, 0, 0, 0);
    assertArrayEquals(new String[] {"a", "d"}, r);

    a = new String[] {"a", "b", "d"};
    b = new String[] {"a", "c", "d"};
    r = AssembleUtil.retain(a, b, 0, 0, 0);
    assertArrayEquals(new String[] {"a", "d"}, r);

    a = new String[] {"c", "d"};
    b = new String[] {"a", "b", "d"};
    r = AssembleUtil.retain(a, b, 0, 0, 0);
    assertArrayEquals(new String[] {"d"}, r);

    a = new String[] {"a", "b"};
    b = new String[] {"a", "c"};
    r = AssembleUtil.retain(a, b, 0, 0, 0);
    assertArrayEquals(new String[] {"a"}, r);

    a = new String[] {"a", "b"};
    b = new String[] {"c", "d"};
    r = AssembleUtil.retain(a, b, 0, 0, 0);
    assertNull(r);
  }

  @Test
  public void testContainsAll() {
    String[] a, b;

    a = new String[] {"a", "b", "c", "d"};
    b = new String[] {"a", "b", "c", "d"};
    assertTrue(AssembleUtil.containsAll(a, b));

    a = new String[] {"b", "c", "d"};
    b = new String[] {"a", "b", "c", "d"};
    assertFalse(AssembleUtil.containsAll(a, b));

    a = new String[] {"a", "b", "c", "d"};
    b = new String[] {"b", "c", "d"};
    assertTrue(AssembleUtil.containsAll(a, b));

    a = new String[] {"a", "b", "c"};
    b = new String[] {"a", "b", "c", "d"};
    assertFalse(AssembleUtil.containsAll(a, b));

    a = new String[] {"a", "b", "c", "d"};
    b = new String[] {"a", "b", "c"};
    assertTrue(AssembleUtil.containsAll(a, b));

    a = new String[] {"a", "b", "d"};
    b = new String[] {"a", "b", "c", "d"};
    assertFalse(AssembleUtil.containsAll(a, b));

    a = new String[] {"a", "b", "c", "d"};
    b = new String[] {"a", "b", "d"};
    assertTrue(AssembleUtil.containsAll(a, b));

    a = new String[] {"a", "c", "d"};
    b = new String[] {"a", "b", "c", "d"};
    assertFalse(AssembleUtil.containsAll(a, b));

    a = new String[] {"a", "b", "c", "d"};
    b = new String[] {"a", "c", "d"};
    assertTrue(AssembleUtil.containsAll(a, b));

    a = new String[] {"a", "d"};
    b = new String[] {"a", "b", "c", "d"};
    assertFalse(AssembleUtil.containsAll(a, b));

    a = new String[] {"a", "b", "c", "d"};
    b = new String[] {"a", "d"};
    assertTrue(AssembleUtil.containsAll(a, b));

    a = new String[] {"a", "c", "d"};
    b = new String[] {"a", "b", "d"};
    assertFalse(AssembleUtil.containsAll(a, b));

    a = new String[] {"a", "b", "d"};
    b = new String[] {"a", "c", "d"};
    assertFalse(AssembleUtil.containsAll(a, b));

    a = new String[] {"c", "d"};
    b = new String[] {"a", "b", "d"};
    assertFalse(AssembleUtil.containsAll(a, b));

    a = new String[] {"a", "b"};
    b = new String[] {"a", "c"};
    assertFalse(AssembleUtil.containsAll(a, b));

    a = new String[] {"a", "b"};
    b = new String[] {"c", "d"};
    assertFalse(AssembleUtil.containsAll(a, b));
  }

  private static void testRegexMatch(final String pluginName, final String expectedRegex, final String ... tests) {
    assertEquals(expectedRegex, AssembleUtil.convertToNameRegex(pluginName).pattern());
    for (final String test : tests)
      assertTrue(test.matches(expectedRegex));
  }

  @Test
  public void testConvertToNameRegex() {
    try {
      AssembleUtil.convertToNameRegex(null);
      fail("Expected NullPointerException");
    }
    catch (final NullPointerException e) {
    }

    try {
      AssembleUtil.convertToNameRegex("");
      fail("Expected IllegalArgumentException");
    }
    catch (final IllegalArgumentException e) {
    }

    testRegexMatch("*", "^.*", "spring:webmvc",
      "okhttp", "lettuce", "jdbc", "spring:boot", "spring:web:3", "spring:web:4", "spring:web:5", "spring:webmvc", "spring:webmvc:3", "spring:webmvc:4", "spring:webmvc:5");
    testRegexMatch("spring:*", "^spring:.*",
      "spring:boot", "spring:web:3", "spring:web:4", "spring:web:5", "spring:webmvc", "spring:webmvc:3", "spring:webmvc:4", "spring:webmvc:5");
    testRegexMatch("spring:*:*", "^spring:[^:]*:.*",
      "spring:web:3", "spring:web:4", "spring:web:5", "spring:webmvc:3", "spring:webmvc:4", "spring:webmvc:5");
    testRegexMatch("spring:boot", "(^spring:boot$|^spring:boot:.*)",
      "spring:boot");
    testRegexMatch("spring:webmvc", "(^spring:webmvc$|^spring:webmvc:.*)",
      "spring:webmvc", "spring:webmvc:3", "spring:webmvc:4", "spring:webmvc:5");
    testRegexMatch("spring:web", "(^spring:web$|^spring:web:.*)",
      "spring:web:3", "spring:web:4", "spring:web:5");

    testRegexMatch("lettuce:5.?", "^lettuce:5\\..",
      "lettuce:5.0", "lettuce:5.1", "lettuce:5.2");
    testRegexMatch("lettuce:5", "^lettuce:5.*",
      "lettuce:5", "lettuce:5.1", "lettuce:5.2");
    testRegexMatch("lettuce", "(^lettuce$|^lettuce:.*)",
      "lettuce:5", "lettuce:5.0", "lettuce:5.1", "lettuce:5.2");
  }

  @Test
  public void testGetName() {
    try {
      AssembleUtil.getName("");
      fail("Expected IllegalArgumentException");
    }
    catch (final IllegalArgumentException e) {
    }

    final String name = "opentracing-specialagent-1.3.3-SNAPSHOT.jar";
    final String s = File.separator;
    assertEquals(name, AssembleUtil.getName(name));
    assertEquals(name, AssembleUtil.getName("." + s + name));
    assertEquals(name, AssembleUtil.getName("foo" + s + "bar" + s + name));
  }
}