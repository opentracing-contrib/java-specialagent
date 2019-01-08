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

import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Enumeration;

import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import io.opentracing.Span;
import io.opentracing.Tracer;
import net.bytebuddy.agent.ByteBuddyAgent;

/**
 * Test class to validate proper functioning of {@link ClassLoaderAgent} and
 * {@code classloader.btm}.
 * <p>
 * <i><b>Note</b>: This test class is only runnable via SureFire or FailSafe
 * plugins with
 * argLine="-Xbootclasspath/a:${project.build.outputDirectory}".</i>
 *
 * @author Seva Safris
 */
public abstract class ClassLoaderAgentTest {
  static {
    assertNull("This test can only be executed from SureFire or FailSafe plugins with argLine=\"-Xbootclasspath/a:${project.build.outputDirectory}\"", ClassLoaderAgent.class.getClassLoader());
  }

  public static class ByteBuddyBTest extends ClassLoaderAgentTest {
    @BeforeClass
    public static void beforeClass() throws Exception {
      final Instrumentation instrumentation = ByteBuddyAgent.install();

      AgentAgent.premain(null, instrumentation);
      ClassLoaderAgent.premain(null, instrumentation);
    }
  }

  @Ignore
  public static class BytemanBTest extends ClassLoaderAgentTest {
    @BeforeClass
    public static void beforeClass() throws Exception {
      System.setProperty(Agent.INSTRUMENTER, Instrumenter.BYTEMAN.name());
      final Instrumentation instrumentation = ByteBuddyAgent.install();
      AgentAgent.premain(null, instrumentation);
      Agent.premain(null, instrumentation);
    }
  }

  @Test
  public void testAgentFindClass() throws Exception {
    assertNotNull(Agent.findClass(null, Span.class.getName()));
  }

  @Test
  public void testClassLoaderFindClass() throws Exception {
    try (final URLClassLoader classLoader = new URLClassLoader(new URL[0], null)) {
      final Class<?> cls = Class.forName(Tracer.class.getName(), false, classLoader);
      assertNotNull(cls);
      assertEquals(Tracer.class.getName(), cls.getName());
    }
  }

  @Test
  public void testAgentFindResource() throws Exception {
    assertNotNull(Agent.findResource(null, Span.class.getName().replace('.', '/').concat(".class")));
  }

  @Test
  public void testClassLoaderFindResource() throws IOException {
    try (final URLClassLoader classLoader = new URLClassLoader(new URL[0], null)) {
      assertNotNull(classLoader.findResource(Tracer.class.getName().replace('.', '/').concat(".class")));
    }
  }

  @Test
  public void testAgentFindResources() throws Exception {
    final Enumeration<URL> resources = Agent.findResources(null, Span.class.getName().replace('.', '/').concat(".class"));
    assertNotNull(resources);
    assertTrue(resources.hasMoreElements());
  }

  @Test
  public void testClassLoaderFindResources() throws IOException {
    try (final URLClassLoader classLoader = new URLClassLoader(new URL[0], null)) {
      final Enumeration<URL> resources = classLoader.findResources(Tracer.class.getName().replace('.', '/').concat(".class"));
      assertNotNull(resources);
      assertTrue(resources.hasMoreElements());
    }
  }
}