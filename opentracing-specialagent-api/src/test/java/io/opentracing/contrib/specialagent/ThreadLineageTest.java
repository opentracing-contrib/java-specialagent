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

import java.net.URLClassLoader;
import java.util.concurrent.CountDownLatch;

import org.junit.Test;

public class ThreadLineageTest {
  private static final Class<?>[] systemClasses = {ThreadLineageTest.class, TestThread.class, AgentRule.class, AgentRuleUtil.class};

  static {
    AgentRule.$Access.load();
    AgentRule.$Access.init();
    // Mock a tracer class loader, as if the classes in `systemClasses` belong to a parent class loader
    // All classes other than the ones in `systemClasses` should be loaded by the tracer class loader
    Adapter.tracerClassLoader = new URLClassLoader(AgentRuleUtil.classPathToURLs(System.getProperty("java.class.path")), null) {
      @Override
      public Class<?> loadClass(final String name) throws ClassNotFoundException {
        for (int i = 0; i < systemClasses.length; ++i)
          if (systemClasses[i].getName().equals(name))
            return systemClasses[i];

        return super.loadClass(name);
      }
    };
  }

  private static void startRegularThreads(final CountDownLatch latch) {
    TestThread.launch(true, latch);
  }

  private static void startTracerThreads(final CountDownLatch latch) throws Exception {
    final Class<?> cls = Adapter.tracerClassLoader.loadClass("io.opentracing.contrib.specialagent.TestTracer");
    assertEquals("Tracer class should be loaded from tracer class loader", Adapter.tracerClassLoader, cls.getClassLoader());
    cls.getMethod("start", CountDownLatch.class).invoke(null, latch);
  }

  @Test
  public void test() throws Exception {
    for (int i = 0; i < systemClasses.length; ++i)
      assertEquals("System class should be loaded from system class loader", systemClasses[i], Adapter.tracerClassLoader.loadClass(systemClasses[i].getName()));

    final CountDownLatch latch = new CountDownLatch(14);

    startRegularThreads(latch);
    startTracerThreads(latch);

    latch.await();
  }
}