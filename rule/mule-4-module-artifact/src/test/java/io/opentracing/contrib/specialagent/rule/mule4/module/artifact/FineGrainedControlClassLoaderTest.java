package io.opentracing.contrib.specialagent.rule.mule4.module.artifact;

import io.opentracing.contrib.specialagent.AgentRunner;
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

import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collections;

// Based on https://github.com/mulesoft/mule/blob/mule-4.2.2/modules/artifact/src/test/java/org/mule/runtime/module/artifact/api/classloader/FineGrainedControlClassLoaderTestCase.java
@AgentRunner.Config(isolateClassLoader = false)
@RunWith(AgentRunner.class)
public class FineGrainedControlClassLoaderTest extends AbstractMuleTestCase {
    public static final String TEST_CLASS_PACKAGE = "mypackage";
    public static final String TEST_CLASS_NAME = "mypackage.MyClass";
    public static final String TEST_CLASS_RESOURCE_NAME = TEST_CLASS_NAME.replace(".", "/") + ".class";

    @Rule
    public ExpectedException expected = ExpectedException.none();

    @Test
    public void lookupPolicyServesResource() throws Exception {
        ClassLoader parent = Thread.currentThread().getContextClassLoader();
        ClassLoader container = new URLClassLoader(new URL[]{this.getResourceURL()}, parent);

        ClassLoaderLookupPolicy lookupPolicy1 = new MuleClassLoaderLookupPolicy(Collections.singletonMap(TEST_CLASS_PACKAGE, new ContainerOnlyLookupStrategy(container)), Collections.emptySet());
        FineGrainedControlClassLoader ext = new FineGrainedControlClassLoader(new URL[]{}, parent, lookupPolicy1);

        URL resource = container.getResource(TEST_CLASS_RESOURCE_NAME);
        URL resource1 = ext.getResource(TEST_CLASS_RESOURCE_NAME);

        Assert.assertNotNull(resource);
        Assert.assertNotNull(resource1);
    }


    private URL getResourceURL() {
        return ClassUtils.getResource("classloader-test-hello.jar", this.getClass());
    }

}
