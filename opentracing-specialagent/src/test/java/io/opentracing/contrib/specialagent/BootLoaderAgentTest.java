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

import java.io.File;
import java.lang.instrument.Instrumentation;
import java.util.jar.JarFile;

import org.junit.Test;

public class BootLoaderAgentTest {
  private static final Instrumentation inst = AgentRunner.install();

  static {
    try {
      final JarFile tempJarFile = SpecialAgentUtil.createTempJarFile(new File("src/test/java"));
      inst.appendToBootstrapClassLoaderSearch(tempJarFile);
    }
    catch (final Exception e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  @Test
  public void test() {
    final String resourceName = getClass().getName().replace('.', '/').concat(".java");
    assertNotNull(ClassLoader.getSystemClassLoader().getResource(resourceName));
  }
}