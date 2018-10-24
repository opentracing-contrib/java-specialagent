package io.opentracing.contrib.instrumenter;

import static org.junit.Assert.*;

import java.net.URLClassLoader;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.opentracing.mock.MockTracer;

@RunWith(TestRunner.class)
public class InstrumenterRunnerTest {
  private static void assertClassLoader() {
    System.err.println("  " + new Exception().getStackTrace()[1]);
    assertEquals(URLClassLoader.class, InstrumenterRunnerTest.class.getClassLoader().getClass());
  }

  @BeforeClass
  public static void beforeClass(final MockTracer tracer) {
    assertClassLoader();
  }

  @AfterClass
  public static void afterClass(final MockTracer tracer) {
    assertClassLoader();
  }

  @Before
  public void before(final MockTracer tracer) {
    assertClassLoader();
  }

  @After
  public void after(final MockTracer tracer) {
    assertClassLoader();
//    throw new RuntimeException();
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