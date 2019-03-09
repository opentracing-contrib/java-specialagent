package io.opentracing.contrib.specialagent;

import static org.junit.Assert.*;

import org.junit.Test;

public class AgentRuleUtilTest {
  @Test
  public void testSubArray() {
    try {
      AgentRuleUtil.subArray(null, 0);
      fail("Expected NullPointerException");
    }
    catch (final NullPointerException e) {
    }

    try {
      AgentRuleUtil.subArray(new String[] {""}, 0, -1);
      fail("Expected IllegalArgumentException");
    }
    catch (final IllegalArgumentException e) {
    }

    final Integer[] array = {0, 1, 2, 3, 4, 5, 6, 7, 8};
    assertArrayEquals(new Integer[] {2, 3}, AgentRuleUtil.subArray(array, 2, 4));
    assertArrayEquals(new Integer[] {6, 7, 8}, AgentRuleUtil.subArray(array, 6));
  }
}