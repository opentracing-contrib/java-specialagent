package io.opentracing.contrib.specialagent;

import static org.junit.Assert.*;

import org.junit.Test;

public class ManagerTest {
  @Test
  public void testRetrofitScript() {
    final String expected = Util.readBytes(Thread.currentThread().getContextClassLoader().getResource("control.btm"));
    final String test = Util.readBytes(Thread.currentThread().getContextClassLoader().getResource("test.btm"));
    final String actual = Manager.retrofitScript(test, 0);
    assertEquals(actual, expected, actual);
  }
}