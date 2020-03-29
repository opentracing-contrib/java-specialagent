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

package io.opentracing.contrib.specialagent.rule.mule4.module.artifact;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collections;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mule.runtime.container.internal.ContainerOnlyLookupStrategy;
import org.mule.runtime.container.internal.MuleClassLoaderLookupPolicy;
import org.mule.runtime.core.api.util.ClassUtils;
import org.mule.runtime.module.artifact.api.classloader.ClassLoaderLookupPolicy;
import org.mule.runtime.module.artifact.api.classloader.FineGrainedControlClassLoader;
import org.mule.tck.junit4.AbstractMuleTestCase;

import io.opentracing.contrib.specialagent.AgentRunner;

// Based on https://github.com/mulesoft/mule/blob/mule-4.2.2/modules/artifact/src/test/java/org/mule/runtime/module/artifact/api/classloader/FineGrainedControlClassLoaderTestCase.java
@RunWith(AgentRunner.class)
public class FineGrainedControlClassLoaderTest extends AbstractMuleTestCase {
  public static final String TEST_CLASS_PACKAGE = "mypackage";
  public static final String TEST_CLASS_NAME = "mypackage.MyClass";
  public static final String TEST_CLASS_RESOURCE_NAME = TEST_CLASS_NAME.replace(".", "/") + ".class";

  @Rule
  public ExpectedException expected = ExpectedException.none();

  @Test
  public void lookupPolicyServesResource() throws IOException {
    final ClassLoader parent = Thread.currentThread().getContextClassLoader();
    final ClassLoader container = new URLClassLoader(new URL[] {getResourceURL()}, parent);

    final ClassLoaderLookupPolicy lookupPolicy1 = new MuleClassLoaderLookupPolicy(Collections.singletonMap(TEST_CLASS_PACKAGE, new ContainerOnlyLookupStrategy(container)), Collections.emptySet());
    try (final FineGrainedControlClassLoader ext = new FineGrainedControlClassLoader(new URL[] {}, parent, lookupPolicy1)) {
      final URL resource = container.getResource(TEST_CLASS_RESOURCE_NAME);
      final URL resource1 = ext.getResource(TEST_CLASS_RESOURCE_NAME);

      Assert.assertNotNull(resource);
      Assert.assertNotNull(resource1);
    }
  }

  private URL getResourceURL() {
    return ClassUtils.getResource("classloader-test-hello.jar", this.getClass());
  }
}