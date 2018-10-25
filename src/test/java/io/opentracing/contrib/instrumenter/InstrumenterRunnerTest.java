package io.opentracing.contrib.instrumenter;

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

@RunWith(TestRunner.class)
@InstrumenterRunner.Debug(true)
public class InstrumenterRunnerTest {
  private static final Logger logger = Logger.getLogger(InstrumenterRunnerTest.class.getName());

  private static void assertClassLoader() {
    logger.fine("  " + new Exception().getStackTrace()[1]);
    assertEquals(URLClassLoader.class, InstrumenterRunnerTest.class.getClassLoader().getClass());
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