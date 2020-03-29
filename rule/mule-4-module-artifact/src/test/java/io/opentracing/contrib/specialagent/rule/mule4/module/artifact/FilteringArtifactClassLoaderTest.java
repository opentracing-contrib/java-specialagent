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

import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collections;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mule.runtime.core.api.util.ClassUtils;
import org.mule.runtime.module.artifact.api.classloader.DefaultArtifactClassLoaderFilter;
import org.mule.runtime.module.artifact.api.classloader.FilteringArtifactClassLoader;
import org.mule.tck.junit4.AbstractMuleTestCase;

import io.opentracing.contrib.specialagent.AgentRunner;
import io.opentracing.contrib.specialagent.rule.mule4.module.artifact.copied.DelegatingArtifactClassLoader;

// Based on https://github.com/mulesoft/mule/blob/mule-4.2.2/modules/artifact/src/test/java/org/mule/runtime/module/artifact/api/classloader/FilteringArtifactClassLoaderTestCase.java
@RunWith(AgentRunner.class)
public class FilteringArtifactClassLoaderTest extends AbstractMuleTestCase {
  public static final String TEST_CLASS_PACKAGE = "mypackage";
  public static final String TEST_CLASS_NAME = "mypackage.MyClass";
  public static final String TEST_CLASS_RESOURCE_NAME = TEST_CLASS_NAME.replace(".", "/") + ".class";

  @Rule
  public ExpectedException expected = ExpectedException.none();

  @Test
  public void lookupPolicyServesResource() throws Exception {
    final ClassLoader parent = Thread.currentThread().getContextClassLoader();
    final ClassLoader target = new URLClassLoader(new URL[] {getResourceURL()}, parent);

    final DefaultArtifactClassLoaderFilter filter = new DefaultArtifactClassLoaderFilter(Collections.singleton(TEST_CLASS_PACKAGE), Collections.emptySet());
    final FilteringArtifactClassLoader filteringLoader = new FilteringArtifactClassLoader(parent, new DelegatingArtifactClassLoader(target), filter, Collections.emptyList());

    final URL resource = target.getResource(TEST_CLASS_RESOURCE_NAME);
    final URL resource1 = filteringLoader.getResource(TEST_CLASS_RESOURCE_NAME);

    Assert.assertNotNull(resource);
    Assert.assertNotNull(resource1);
  }

  private URL getResourceURL() {
    return ClassUtils.getResource("classloader-test-hello.jar", this.getClass());
  }
}