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

import org.junit.Test;

public class BytemanTransformerTest {
  @Test
  public void testRetrofitScript() {
    final String expected = Util.readBytes(Thread.currentThread().getContextClassLoader().getResource("control.btm"));
    final String test = Util.readBytes(Thread.currentThread().getContextClassLoader().getResource("test.btm"));
    final String actual = BytemanManager.retrofitScript(test, 0);
    assertEquals(actual, expected, actual);
  }
}