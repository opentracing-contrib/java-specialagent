/* Copyright 2020 The OpenTracing Authors
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

package io.opentracing.contrib.specialagent.rule.dynamic;

import static org.junit.Assert.*;

import org.junit.Test;

public class DynamicSpecTest {
  private static void assertSpec(final String rule, final boolean polymorphic, final String className, final String methodName, final String[] args, final String returning) {
    final DynamicSpec spec = DynamicSpec.parseRules(rule)[0];
    assertEquals(polymorphic, spec.polymorphic);
    assertEquals(className, spec.className);
    assertEquals(methodName, spec.methodName);
    assertArrayEquals(args, spec.args);
    assertEquals(returning, spec.returning);
  }

  @Test
  public void testErrors() {
    try {
      DynamicSpec.parseRules(null);
      fail("Expected NullPointerException");
    }
    catch (final NullPointerException e) {
    }

    try {
      DynamicSpec.parseRules("");
      fail("Expected IllegalArgumentException");
    }
    catch (final IllegalArgumentException e) {
    }

    try {
      DynamicSpec.parseRules("java.lang.Object");
      fail("Expected IllegalArgumentException");
    }
    catch (final IllegalArgumentException e) {
    }

    try {
      DynamicSpec.parseRules("#toString:java.lang.String");
      fail("Expected IllegalArgumentException");
    }
    catch (final IllegalArgumentException e) {
    }

    try {
      DynamicSpec.parseRules("java.lang.Object#");
      fail("Expected IllegalArgumentException");
    }
    catch (final IllegalArgumentException e) {
    }

    try {
      DynamicSpec.parseRules("java.lang.Object#:java.lang.String");
      fail("Expected IllegalArgumentException");
    }
    catch (final IllegalArgumentException e) {
    }

    try {
      DynamicSpec.parseRules("java.lang.Object#toString()::");
      fail("Expected IllegalArgumentException");
    }
    catch (final IllegalArgumentException e) {
    }

    try {
      DynamicSpec.parseRules("java.lang.Object#():java.lang.String");
      fail("Expected IllegalArgumentException");
    }
    catch (final IllegalArgumentException e) {
    }

    try {
      DynamicSpec.parseRules("java.lang.Object#toString():java.lang.String:");
      fail("Expected IllegalArgumentException");
    }
    catch (final IllegalArgumentException e) {
    }

    try {
      DynamicSpec.parseRules("java.lang.Object#wait():<void>:~java.lang.Exception");
      fail("Expected IllegalArgumentException");
    }
    catch (final IllegalArgumentException e) {
    }

    try {
      DynamicSpec.parseRules("java.lang.Object#<init>():<void>");
      fail("Expected IllegalArgumentException");
    }
    catch (final IllegalArgumentException e) {
    }

    try {
      DynamicSpec.parseRules("java.lang.Object#<clinit>():<void>");
      fail("Expected IllegalArgumentException");
    }
    catch (final IllegalArgumentException e) {
    }

    try {
      DynamicSpec.parseRules("java.lang.Object#<clinit>()");
      fail("Expected IllegalArgumentException");
    }
    catch (final IllegalArgumentException e) {
    }
  }

  @Test
  public void test1() {
    final String rule = "java.lang.Object#toString:java.lang.String";
    assertSpec(rule, false, "java.lang.Object", "toString", null, "java.lang.String");
  }

  @Test
  public void test2() {
    final String rule = "^java.lang.Object#toString()";
    assertSpec(rule, true, "java.lang.Object", "toString", new String[0], null);
  }

  @Test
  public void test3() {
    final String rule = "java.lang.Object#wait():<void>";
    assertSpec(rule, false, "java.lang.Object", "wait", new String[0], "<void>");
  }
}