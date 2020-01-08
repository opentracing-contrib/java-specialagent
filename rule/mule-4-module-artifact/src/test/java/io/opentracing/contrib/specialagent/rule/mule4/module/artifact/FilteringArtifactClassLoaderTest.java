package io.opentracing.contrib.specialagent.rule.mule4.module.artifact;

import io.opentracing.contrib.specialagent.AgentRunner;
import io.opentracing.contrib.specialagent.rule.mule4.module.artifact.copied.DelegatingArtifactClassLoader;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mule.runtime.core.api.util.ClassUtils;
import org.mule.runtime.module.artifact.api.classloader.*;
import org.mule.tck.junit4.AbstractMuleTestCase;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collections;

// Based on https://github.com/mulesoft/mule/blob/mule-4.2.2/modules/artifact/src/test/java/org/mule/runtime/module/artifact/api/classloader/FilteringArtifactClassLoaderTestCase.java
@AgentRunner.Config(isolateClassLoader = false)
@RunWith(AgentRunner.class)
public class FilteringArtifactClassLoaderTest extends AbstractMuleTestCase {
    public static final String TEST_CLASS_PACKAGE = "mypackage";
    public static final String TEST_CLASS_NAME = "mypackage.MyClass";
    public static final String TEST_CLASS_RESOURCE_NAME = TEST_CLASS_NAME.replace(".", "/") + ".class";

    @Rule
    public ExpectedException expected = ExpectedException.none();

    @Test
    public void lookupPolicyServesResource() throws Exception {
        ClassLoader parent = Thread.currentThread().getContextClassLoader();
        ClassLoader target = new URLClassLoader(new URL[]{this.getResourceURL()}, parent);

        DefaultArtifactClassLoaderFilter filter = new DefaultArtifactClassLoaderFilter(Collections.singleton(TEST_CLASS_PACKAGE), Collections.emptySet());
        FilteringArtifactClassLoader filteringLoader = new FilteringArtifactClassLoader(parent, new DelegatingArtifactClassLoader(target), filter, Collections.emptyList());

        URL resource = target.getResource(TEST_CLASS_RESOURCE_NAME);
        URL resource1 = filteringLoader.getResource(TEST_CLASS_RESOURCE_NAME);

        Assert.assertNotNull(resource);
        Assert.assertNotNull(resource1);
    }


    private URL getResourceURL() {
        return ClassUtils.getResource("classloader-test-hello.jar", this.getClass());
    }

}
