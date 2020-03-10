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

import java.util.concurrent.CountDownLatch;

public class TestThread extends Thread {
  static {
    assertEquals("TestThread must be loaded by the system class loader", ClassLoader.getSystemClassLoader(), TestTracer.class.getClassLoader());
  }

  /**
   * Launch 1 thread with 2 child threads having 2 child threads of their own.
   *
   * @param shouldBeEnabled The value {@link AgentRule#isEnabled(String,String)}
   *          should return for the spawned threads.
   * @param latch The {@link CountDownLatch}.
   */
  public static void launch(final boolean shouldBeEnabled, final CountDownLatch latch) {
    new TestThread(shouldBeEnabled, latch) {
      @Override
      public void run() {
        super.run();
        for (int i = 0; i < 2; ++i)
          new TestThread(shouldBeEnabled, latch) {
            @Override
            public void run() {
              super.run();
              for (int i = 0; i < 2; ++i)
                new TestThread(shouldBeEnabled, latch).start();
            }
        }.start();
      }
    }.start();
  }

  private final boolean shouldBeEnabled;
  private final CountDownLatch latch;

  public TestThread(final boolean shouldBeEnabled, final CountDownLatch latch) {
    super(shouldBeEnabled ? "Enabled" : "Disabled");
    this.shouldBeEnabled = shouldBeEnabled;
    this.latch = latch;
  }

  @Override
  public void run() {
    try {
      assertEquals(shouldBeEnabled, AgentRule.isEnabled(getName(), String.valueOf(getId())));
    }
    finally {
      latch.countDown();
    }
  }
}