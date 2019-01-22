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

import java.net.URLClassLoader;
import java.util.logging.Logger;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.opentracing.mock.MockTracer;

@RunWith(AgentRunner.class)
@AgentRunner.Config(debug=true)
@SuppressWarnings("unused")
public class AgentRunnerITest {
  private static final Logger logger = Logger.getLogger(AgentRunnerITest.class.getName());

  private static void assertClassLoader() {
    logger.fine("  " + new Exception().getStackTrace()[1]);
    assertEquals(URLClassLoader.class, AgentRunnerITest.class.getClassLoader().getClass());
  }

  @BeforeClass
  public static void beforeClass1(final MockTracer tracer) {
    assertClassLoader();
  }

  @BeforeClass
  public static void beforeClass2(final MockTracer tracer) {
    assertClassLoader();
  }

  @AfterClass
  public static void afterClass1(final MockTracer tracer) {
    assertClassLoader();
  }

  @AfterClass
  public static void afterClass2(final MockTracer tracer) {
    assertClassLoader();
//    throw new RuntimeException();
  }

  @Before
  public void before1(final MockTracer tracer) {
    assertClassLoader();
  }

  @Before
  public void before2(final MockTracer tracer) {
    assertClassLoader();
  }

  @After
  public void after1(final MockTracer tracer) {
    assertClassLoader();
  }

  @After
  public void after2(final MockTracer tracer) {
    assertClassLoader();
  }

  @Test
  public void test1(final MockTracer tracer) {
    assertClassLoader();
  }

  @Test
  public void test2(final MockTracer tracer) {
    assertClassLoader();
  }

  @Test
  public void test3(final MockTracer tracer) {
    assertClassLoader();
  }

  @Test
  @Ignore
  public void ignored(final MockTracer tracer) {
    assertClassLoader();
  }
}