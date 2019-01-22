package io.opentracing.contrib.specialagent;

import static org.junit.Assert.*;

import org.junit.Test;

public class AgentPluginUtilTest {
  @Test
  public void testSubArray() {
    try {
      AgentPluginUtil.subArray(null, 0);
      fail("Expected NullPointerException");
    }
    catch (final NullPointerException e) {
    }

    try {
      AgentPluginUtil.subArray(new String[] {""}, 0, -1);
      fail("Expected IllegalArgumentException");
    }
    catch (final IllegalArgumentException e) {
    }

    final Integer[] array = {0, 1, 2, 3, 4, 5, 6, 7, 8};
    assertArrayEquals(new Integer[] {2, 3}, AgentPluginUtil.subArray(array, 2, 4));
    assertArrayEquals(new Integer[] {6, 7, 8}, AgentPluginUtil.subArray(array, 6));
  }
}