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
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Enumeration;
import java.util.List;

import org.junit.Ignore;
import org.junit.Test;

public class FingerprintTest {
  private static final Logger logger = Logger.getLogger(FingerprintTest.class);

  @Test
  public void test2() throws IOException {
    FingerprintBuilder.debugVisitor = false;
    Logger.setLevel(Level.FINEST);
    final List<ClassFingerprint> classFingerprints = new FingerprintBuilder(logger).build(ClassLoader.getSystemClassLoader(), Integer.MAX_VALUE, FpTestClass1.class, FpTestClass2.MemberInner.class, FpTestClass2.Inner.class, FpTestClass2.class);
    System.out.println(AssembleUtil.toIndentedString(classFingerprints));
  }

  @Test
  @Ignore
  public void test1() throws IOException {
    final Enumeration<URL> urls = Thread.currentThread().getContextClassLoader().getResources("javax/el/ELClass.class");
    URL jarURL = null;
    while (urls.hasMoreElements() && jarURL == null) {
      final URL url = urls.nextElement();
      if ("jar".equals(url.getProtocol()))
        jarURL = new URL("file", "", url.getPath().substring(5, url.getPath().indexOf('!')));
    }

    if (jarURL == null)
      fail("Could not find JAR resource");

    final LibraryFingerprint lib = new LibraryFingerprint(new URLClassLoader(new URL[] {jarURL}, ClassLoader.getSystemClassLoader()), null, null, logger);
    logger.fine(lib.toString());
    assertEquals(37, lib.getClasses().length);

    ClassFingerprint fingerprint;
    ConstructorFingerprint constructor;
    MethodFingerprint method;

    fingerprint = lib.getClasses()[0];
    assertEquals("javax.el.ArrayELResolver", fingerprint.getName());
    assertEquals("javax.el.ELResolver", fingerprint.getSuperClass());

    assertNull(fingerprint.getFields());

    assertEquals(2, fingerprint.getConstructors().size());
    constructor = fingerprint.getConstructors().get(0);
    assertNull(constructor.getParameterTypes());
    assertNull(constructor.getExceptionTypes());
    constructor = fingerprint.getConstructors().get(1);
    assertEquals(1, constructor.getParameterTypes().size());
    assertEquals("boolean", constructor.getParameterTypes().get(0));
    assertNull(constructor.getExceptionTypes());

    assertEquals(6, fingerprint.getMethods().size());

    method = fingerprint.getMethods().get(0);
    assertEquals("getCommonPropertyType", method.getName());
    assertEquals("java.lang.Class", method.getReturnType());
    assertEquals(2, method.getParameterTypes().size());
    assertEquals("javax.el.ELContext", method.getParameterTypes().get(0));
    assertEquals("java.lang.Object", method.getParameterTypes().get(1));
    assertNull(method.getExceptionTypes());

    method = fingerprint.getMethods().get(1);
    assertNull(method.getExceptionTypes());
    assertEquals("getFeatureDescriptors", method.getName());
    assertEquals("java.util.Iterator", method.getReturnType());
    assertEquals(2, method.getParameterTypes().size());
    assertEquals("javax.el.ELContext", method.getParameterTypes().get(0));
    assertEquals("java.lang.Object", method.getParameterTypes().get(1));
    assertNull(method.getExceptionTypes());

    method = fingerprint.getMethods().get(2);
    assertEquals("getType", method.getName());
    assertEquals("java.lang.Class", method.getReturnType());
    assertEquals(3, method.getParameterTypes().size());
    assertEquals("javax.el.ELContext", method.getParameterTypes().get(0));
    assertEquals("java.lang.Object", method.getParameterTypes().get(1));
    assertEquals("java.lang.Object", method.getParameterTypes().get(2));
    assertNull(method.getExceptionTypes());

    method = fingerprint.getMethods().get(3);
    assertEquals("getValue", method.getName());
    assertEquals("java.lang.Object", method.getReturnType());
    assertEquals(3, method.getParameterTypes().size());
    assertEquals("javax.el.ELContext", method.getParameterTypes().get(0));
    assertEquals("java.lang.Object", method.getParameterTypes().get(1));
    assertEquals("java.lang.Object", method.getParameterTypes().get(2));
    assertNull(method.getExceptionTypes());

    method = fingerprint.getMethods().get(4);
    assertEquals("isReadOnly", method.getName());
    assertEquals("boolean", method.getReturnType());
    assertEquals(3, method.getParameterTypes().size());
    assertEquals("javax.el.ELContext", method.getParameterTypes().get(0));
    assertEquals("java.lang.Object", method.getParameterTypes().get(1));
    assertEquals("java.lang.Object", method.getParameterTypes().get(2));
    assertNull(method.getExceptionTypes());

    method = fingerprint.getMethods().get(5);
    assertEquals("setValue", method.getName());
    assertNull(method.getReturnType());
    assertEquals(4, method.getParameterTypes().size());
    assertEquals("javax.el.ELContext", method.getParameterTypes().get(0));
    assertEquals("java.lang.Object", method.getParameterTypes().get(1));
    assertEquals("java.lang.Object", method.getParameterTypes().get(2));
    assertEquals("java.lang.Object", method.getParameterTypes().get(3));
    assertNull(method.getExceptionTypes());

    fingerprint = lib.getClasses()[1];
    assertEquals("javax.el.BeanELResolver", fingerprint.getName());
    assertEquals("javax.el.ELResolver", fingerprint.getSuperClass());

    assertNull(fingerprint.getFields());

    assertEquals(2, fingerprint.getConstructors().size());
    constructor = fingerprint.getConstructors().get(0);
    assertNull(constructor.getParameterTypes());
    assertNull(constructor.getExceptionTypes());
    constructor = fingerprint.getConstructors().get(1);
    assertEquals(1, constructor.getParameterTypes().size());
    assertEquals("boolean", constructor.getParameterTypes().get(0));
    assertNull(constructor.getExceptionTypes());

    assertEquals(7, fingerprint.getMethods().size());

    method = fingerprint.getMethods().get(0);
    assertEquals("getCommonPropertyType", method.getName());
    assertEquals("java.lang.Class", method.getReturnType());
    assertEquals(2, method.getParameterTypes().size());
    assertEquals("javax.el.ELContext", method.getParameterTypes().get(0));
    assertEquals("java.lang.Object", method.getParameterTypes().get(1));
    assertNull(method.getExceptionTypes());

    method = fingerprint.getMethods().get(1);
    assertNull(method.getExceptionTypes());
    assertEquals("getFeatureDescriptors", method.getName());
    assertEquals("java.util.Iterator", method.getReturnType());
    assertEquals(2, method.getParameterTypes().size());
    assertEquals("javax.el.ELContext", method.getParameterTypes().get(0));
    assertEquals("java.lang.Object", method.getParameterTypes().get(1));
    assertNull(method.getExceptionTypes());

    method = fingerprint.getMethods().get(2);
    assertEquals("getType", method.getName());
    assertEquals("java.lang.Class", method.getReturnType());
    assertEquals(3, method.getParameterTypes().size());
    assertEquals("javax.el.ELContext", method.getParameterTypes().get(0));
    assertEquals("java.lang.Object", method.getParameterTypes().get(1));
    assertEquals("java.lang.Object", method.getParameterTypes().get(2));
    assertNull(method.getExceptionTypes());

    method = fingerprint.getMethods().get(3);
    assertEquals("getValue", method.getName());
    assertEquals("java.lang.Object", method.getReturnType());
    assertEquals(3, method.getParameterTypes().size());
    assertEquals("javax.el.ELContext", method.getParameterTypes().get(0));
    assertEquals("java.lang.Object", method.getParameterTypes().get(1));
    assertEquals("java.lang.Object", method.getParameterTypes().get(2));
    assertNull(method.getExceptionTypes());

    method = fingerprint.getMethods().get(4);
    assertEquals("invoke", method.getName());
    assertEquals("java.lang.Object", method.getReturnType());
    assertEquals(5, method.getParameterTypes().size());
    assertEquals("javax.el.ELContext", method.getParameterTypes().get(0));
    assertEquals("java.lang.Object", method.getParameterTypes().get(1));
    assertEquals("java.lang.Object", method.getParameterTypes().get(2));
    assertEquals("java.lang.Class[]", method.getParameterTypes().get(3));
    assertEquals("java.lang.Object[]", method.getParameterTypes().get(4));
    assertNull(method.getExceptionTypes());

    method = fingerprint.getMethods().get(5);
    assertEquals("isReadOnly", method.getName());
    assertEquals("boolean", method.getReturnType());
    assertEquals(3, method.getParameterTypes().size());
    assertEquals("javax.el.ELContext", method.getParameterTypes().get(0));
    assertEquals("java.lang.Object", method.getParameterTypes().get(1));
    assertEquals("java.lang.Object", method.getParameterTypes().get(2));
    assertNull(method.getExceptionTypes());

    method = fingerprint.getMethods().get(6);
    assertEquals("setValue", method.getName());
    assertNull(method.getReturnType());
    assertEquals(4, method.getParameterTypes().size());
    assertEquals("javax.el.ELContext", method.getParameterTypes().get(0));
    assertEquals("java.lang.Object", method.getParameterTypes().get(1));
    assertEquals("java.lang.Object", method.getParameterTypes().get(2));
    assertEquals("java.lang.Object", method.getParameterTypes().get(3));
    assertNull(method.getExceptionTypes());

    fingerprint = lib.getClasses()[2];
    assertEquals("javax.el.BeanELResolver$BeanProperties", fingerprint.getName());
    assertNull(fingerprint.getSuperClass());

    assertNull(fingerprint.getFields());

    assertEquals(1, fingerprint.getConstructors().size());
    constructor = fingerprint.getConstructors().get(0);
    assertEquals(1, constructor.getParameterTypes().size());
    assertEquals("java.lang.Class", constructor.getParameterTypes().get(0));
    assertNull(constructor.getExceptionTypes());

    assertEquals(1, fingerprint.getMethods().size());

    method = fingerprint.getMethods().get(0);
    assertEquals("getBeanProperty", method.getName());
    assertEquals("javax.el.BeanELResolver$BeanProperty", method.getReturnType());
    assertEquals(1, method.getParameterTypes().size());
    assertEquals("java.lang.String", method.getParameterTypes().get(0));
    assertNull(method.getExceptionTypes());

    fingerprint = lib.getClasses()[3];
    assertEquals("javax.el.BeanELResolver$BeanProperty", fingerprint.getName());
    assertNull(fingerprint.getSuperClass());

    assertNull(fingerprint.getFields());

    assertEquals(1, fingerprint.getConstructors().size());
    constructor = fingerprint.getConstructors().get(0);
    assertEquals(2, constructor.getParameterTypes().size());
    assertEquals("java.lang.Class", constructor.getParameterTypes().get(0));
    assertEquals("java.beans.PropertyDescriptor", constructor.getParameterTypes().get(1));
    assertNull(constructor.getExceptionTypes());

    assertEquals(4, fingerprint.getMethods().size());

    method = fingerprint.getMethods().get(0);
    assertEquals("getPropertyType", method.getName());
    assertEquals("java.lang.Class", method.getReturnType());
    assertNull(method.getParameterTypes());
    assertNull(method.getExceptionTypes());

    method = fingerprint.getMethods().get(1);
    assertEquals("getReadMethod", method.getName());
    assertEquals("java.lang.reflect.Method", method.getReturnType());
    assertNull(method.getParameterTypes());
    assertNull(method.getExceptionTypes());

    method = fingerprint.getMethods().get(2);
    assertEquals("getWriteMethod", method.getName());
    assertEquals("java.lang.reflect.Method", method.getReturnType());
    assertNull(method.getParameterTypes());
    assertNull(method.getExceptionTypes());

    method = fingerprint.getMethods().get(3);
    assertEquals("isReadOnly", method.getName());
    assertEquals("boolean", method.getReturnType());
    assertNull(method.getParameterTypes());
    assertNull(method.getExceptionTypes());

    fingerprint = lib.getClasses()[4];
    assertEquals("javax.el.BeanNameELResolver", fingerprint.getName());
    assertEquals("javax.el.ELResolver", fingerprint.getSuperClass());

    assertNull(fingerprint.getFields());

    assertEquals(1, fingerprint.getConstructors().size());
    constructor = fingerprint.getConstructors().get(0);
    assertEquals(1, constructor.getParameterTypes().size());
    assertEquals("javax.el.BeanNameResolver", constructor.getParameterTypes().get(0));
    assertNull(constructor.getExceptionTypes());

    assertEquals(6, fingerprint.getMethods().size());

    method = fingerprint.getMethods().get(0);
    assertEquals("getCommonPropertyType", method.getName());
    assertEquals("java.lang.Class", method.getReturnType());
    assertEquals(2, method.getParameterTypes().size());
    assertEquals("javax.el.ELContext", method.getParameterTypes().get(0));
    assertEquals("java.lang.Object", method.getParameterTypes().get(1));
    assertNull(method.getExceptionTypes());

    method = fingerprint.getMethods().get(1);
    assertEquals("getFeatureDescriptors", method.getName());
    assertEquals("java.util.Iterator", method.getReturnType());
    assertEquals(2, method.getParameterTypes().size());
    assertEquals("javax.el.ELContext", method.getParameterTypes().get(0));
    assertEquals("java.lang.Object", method.getParameterTypes().get(1));
    assertNull(method.getExceptionTypes());

    method = fingerprint.getMethods().get(2);
    assertEquals("getType", method.getName());
    assertEquals("java.lang.Class", method.getReturnType());
    assertEquals(3, method.getParameterTypes().size());
    assertEquals("javax.el.ELContext", method.getParameterTypes().get(0));
    assertEquals("java.lang.Object", method.getParameterTypes().get(1));
    assertEquals("java.lang.Object", method.getParameterTypes().get(2));
    assertNull(method.getExceptionTypes());

    method = fingerprint.getMethods().get(3);
    assertEquals("getValue", method.getName());
    assertEquals("java.lang.Object", method.getReturnType());
    assertEquals(3, method.getParameterTypes().size());
    assertEquals("javax.el.ELContext", method.getParameterTypes().get(0));
    assertEquals("java.lang.Object", method.getParameterTypes().get(1));
    assertEquals("java.lang.Object", method.getParameterTypes().get(2));
    assertNull(method.getExceptionTypes());

    method = fingerprint.getMethods().get(4);
    assertEquals("isReadOnly", method.getName());
    assertEquals("boolean", method.getReturnType());
    assertEquals(3, method.getParameterTypes().size());
    assertEquals("javax.el.ELContext", method.getParameterTypes().get(0));
    assertEquals("java.lang.Object", method.getParameterTypes().get(1));
    assertEquals("java.lang.Object", method.getParameterTypes().get(2));
    assertNull(method.getExceptionTypes());

    method = fingerprint.getMethods().get(5);
    assertEquals("setValue", method.getName());
    assertNull(method.getReturnType());
    assertEquals(4, method.getParameterTypes().size());
    assertEquals("javax.el.ELContext", method.getParameterTypes().get(0));
    assertEquals("java.lang.Object", method.getParameterTypes().get(1));
    assertEquals("java.lang.Object", method.getParameterTypes().get(2));
    assertEquals("java.lang.Object", method.getParameterTypes().get(3));
    assertNull(method.getExceptionTypes());

    fingerprint = lib.getClasses()[5];
    assertEquals("javax.el.BeanNameResolver", fingerprint.getName());

    assertNull(fingerprint.getFields());

    assertEquals(1, fingerprint.getConstructors().size());
    constructor = fingerprint.getConstructors().get(0);
    assertNull(constructor.getParameterTypes());
    assertNull(constructor.getExceptionTypes());

    assertEquals(5, fingerprint.getMethods().size());

    method = fingerprint.getMethods().get(0);
    assertEquals("canCreateBean", method.getName());
    assertEquals("boolean", method.getReturnType());
    assertEquals(1, method.getParameterTypes().size());
    assertEquals("java.lang.String", method.getParameterTypes().get(0));
    assertNull(method.getExceptionTypes());

    method = fingerprint.getMethods().get(1);
    assertEquals("getBean", method.getName());
    assertEquals("java.lang.Object", method.getReturnType());
    assertEquals(1, method.getParameterTypes().size());
    assertEquals("java.lang.String", method.getParameterTypes().get(0));
    assertNull(method.getExceptionTypes());

    method = fingerprint.getMethods().get(2);
    assertEquals("isNameResolved", method.getName());
    assertEquals("boolean", method.getReturnType());
    assertEquals(1, method.getParameterTypes().size());
    assertEquals("java.lang.String", method.getParameterTypes().get(0));
    assertNull(method.getExceptionTypes());

    method = fingerprint.getMethods().get(3);
    assertEquals("isReadOnly", method.getName());
    assertEquals("boolean", method.getReturnType());
    assertEquals(1, method.getParameterTypes().size());
    assertEquals("java.lang.String", method.getParameterTypes().get(0));
    assertNull(method.getExceptionTypes());

    method = fingerprint.getMethods().get(4);
    assertEquals("setBeanValue", method.getName());
    assertNull(method.getReturnType());
    assertEquals(2, method.getParameterTypes().size());
    assertEquals("java.lang.String", method.getParameterTypes().get(0));
    assertEquals("java.lang.Object", method.getParameterTypes().get(1));
    assertEquals(1, method.getExceptionTypes().size());
    assertEquals("javax.el.PropertyNotWritableException", method.getExceptionTypes().get(0));
  }
}