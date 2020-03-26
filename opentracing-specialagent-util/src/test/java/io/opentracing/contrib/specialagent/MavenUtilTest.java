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

package io.opentracing.contrib.specialagent;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import org.junit.Test;

/**
 * Tests for methods in {@link MavenUtil}.
 *
 * @author Seva Safris
 */
public class MavenUtilTest {
  @Test
  public void testGetRulePathsAll() throws IOException {
    final Set<String> expected = new HashSet<>();
    expected.add("bcprov-jdk15on-1.50.jar");
    expected.add("hamcrest-core-1.3.jar");
    expected.add("junit-4.12.jar");
    expected.add("mockwebserver-3.6.0.jar");
    expected.add("okhttp-3.6.0.jar");
    expected.add("okio-1.11.0.jar");
    expected.add("opentracing-api-0.32.0.jar");
    expected.add("opentracing-concurrent-0.1.0.jar");
    expected.add("opentracing-mock-0.32.0.jar");
    expected.add("lightstep-tracer-jre-bundle-0.16.1.jar");
    expected.add("opentracing-okhttp3-0.1.0.jar");
    expected.add("specialagent-okhttp-0.0.0-SNAPSHOT.jar");
    expected.add("opentracing-specialagent1-0.0.0-SNAPSHOT-tests.jar");
    expected.add("opentracing-specialagent2-0.0.0-SNAPSHOT-tests.jar");
    expected.add("opentracing-util-0.32.0.jar");

    final String test = new String(AssembleUtil.readBytes(new File("src/test/resources/test.tgf").toURI().toURL()));
    final Set<File> files = MavenUtil.selectFromTgf(test, false, (String)null);
    for (final File file : files)
      assertTrue(file.getName(), expected.contains(file.getName()));
  }

  @Test
  public void testGetRulePathsOptionalTest() throws IOException {
    final Set<String> expected = new HashSet<>();
    expected.add("bcprov-jdk15on-1.50.jar");
    expected.add("hamcrest-core-1.3.jar");
    expected.add("junit-4.12.jar");
    expected.add("mockwebserver-3.6.0.jar");
    expected.add("okhttp-3.6.0.jar");
    expected.add("okio-1.11.0.jar");
    expected.add("opentracing-mock-0.32.0.jar");
    expected.add("opentracing-specialagent1-0.0.0-SNAPSHOT-tests.jar");
    expected.add("opentracing-util-0.32.0.jar");

    final String test = new String(AssembleUtil.readBytes(new File("src/test/resources/test.tgf").toURI().toURL()));
    final Set<File> files = MavenUtil.selectFromTgf(test, true, "test");
    for (final File file : files)
      assertTrue(file.getName(), expected.contains(file.getName()));
  }

  @Test
  public void testGetRulePathsTest() throws IOException {
    final Set<String> expected = new HashSet<>();
    expected.add("bcprov-jdk15on-1.50.jar");
    expected.add("hamcrest-core-1.3.jar");
    expected.add("junit-4.12.jar");
    expected.add("mockwebserver-3.6.0.jar");
    expected.add("opentracing-mock-0.32.0.jar");
    expected.add("opentracing-specialagent1-0.0.0-SNAPSHOT-tests.jar");
    expected.add("opentracing-util-0.32.0.jar");

    final String test = new String(AssembleUtil.readBytes(new File("src/test/resources/test.tgf").toURI().toURL()));
    final Set<File> files = MavenUtil.selectFromTgf(test, false, "test");
    for (final File file : files)
      assertTrue(file.getName(), expected.contains(file.getName()));
  }

  @Test
  public void testGetRulePathsCompile() throws IOException {
    final Set<String> expected = new HashSet<>();
    expected.add("opentracing-concurrent-0.1.0.jar");
    expected.add("opentracing-okhttp3-0.1.0.jar");
    expected.add("specialagent-okhttp-0.0.0-SNAPSHOT.jar");
    expected.add("opentracing-specialagent2-0.0.0-SNAPSHOT-tests.jar");
    expected.add("lightstep-tracer-jre-bundle-0.16.1.jar");
    expected.add("opentracing-api-0.32.0.jar");

    final String test = new String(AssembleUtil.readBytes(new File("src/test/resources/test.tgf").toURI().toURL()));
    final Set<File> files = MavenUtil.selectFromTgf(test, false, "compile");
    for (final File file : files)
      assertTrue(file.getName(), expected.contains(file.getName()));
  }

  @Test
  public void testGetRulePathsCompileExclude() throws IOException {
    final HashSet<String> expected = new HashSet<>();
    expected.add("mockwebserver-3.6.0.jar");
    expected.add("bcprov-jdk15on-1.50.jar");
    expected.add("opentracing-mock-0.32.0.jar");
    expected.add("opentracing-util-0.32.0.jar");
    expected.add("opentracing-specialagent1-0.0.0-SNAPSHOT-tests.jar");
    expected.add("hamcrest-core-1.3.jar");

    final String tgf = new String(AssembleUtil.readBytes(new File("src/test/resources/test.tgf").toURI().toURL()));
    final Set<File> files = MavenUtil.selectFromTgf(tgf, false, "test");
    assertEquals(String.valueOf(files), expected.size(), files.size());
    for (final File file : files)
      assertTrue(file.getName(), expected.contains(file.getName()));
  }
}