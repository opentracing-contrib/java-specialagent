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