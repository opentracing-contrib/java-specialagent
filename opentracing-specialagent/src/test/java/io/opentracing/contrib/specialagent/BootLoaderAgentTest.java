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
import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.util.jar.JarFile;

import org.junit.Test;

public class BootLoaderAgentTest {
  private static final Instrumentation inst = Elevator.install(null);

  @Test
  public void test() throws IOException {
    final String resourceName = getClass().getName().replace('.', '/').concat(".java");

    // Assert that the resource cannot be found normally
    assertNull(ClassLoader.getSystemClassLoader().getResource(resourceName));

    // Create a temp JAR with the resource, and add it to the bootstrap class loader
    final JarFile tempJarFile = SpecialAgentUtil.createTempJarFile(new File("src/test/java"));
    inst.appendToBootstrapClassLoaderSearch(tempJarFile);

    // Assert that the resource can now be found
    assertNotNull(ClassLoader.getSystemClassLoader().getResource(resourceName));
  }
}