/* Copyright 2018 The OpenTracing Authors
 *
 * Licensed under the Apache License, final Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, final either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentracing.contrib.specialagent;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

class Fingerprinter extends ClassVisitor {
  static enum Visibility {
    PRIVATE(Modifier.PRIVATE),
    PROTECTED(Modifier.PROTECTED),
    PACKAGE(Modifier.PUBLIC | Modifier.PROTECTED | Modifier.PRIVATE, 0),
    PUBLIC(Modifier.PUBLIC);

    private final int modifier;
    private final int test;

    Visibility(final int modifier, final int test) {
      this.modifier = modifier;
      this.test = test;
    }

    Visibility(final int modifier) {
      this(modifier, modifier);
    }

    static Visibility get(final int access) {
      for (final Visibility visibility : values())
        if ((access & visibility.modifier) == visibility.test)
          return visibility;

      return null;
    }
  }

  private static boolean isSynthetic(final int mod) {
    return (mod & Opcodes.ACC_SYNTHETIC) != 0;
  }

  Fingerprinter() {
    super(Opcodes.ASM4);
  }

  private String className;
  private String superClass;
  private final List<ConstructorFingerprint> constructors = new ArrayList<>();
  private final List<MethodFingerprint> methods = new ArrayList<>();
  private final List<FieldFingerprint> fields = new ArrayList<>();

  private final Map<String,ClassFingerprint> classNameToFingerprint = new HashMap<>();
  private final Set<String> excludes = new HashSet<>();

  ClassFingerprint[] fingerprint(final URLClassLoader classLoader) throws IOException {
    for (final URL url : classLoader.getURLs()) {
      try (final ZipInputStream in = new ZipInputStream(url.openStream())) {
        for (ZipEntry entry; (entry = in.getNextEntry()) != null;) {
          final String name = entry.getName();
          if (name.endsWith(".class") && !name.startsWith("META-INF/") && !name.startsWith("module-info")) {
            final ClassFingerprint classFingerprint = fingerprint(classLoader, name);
            if (classFingerprint != null)
              classNameToFingerprint.put(classFingerprint.getName(), classFingerprint);
          }
        }
      }
    }

    classNameToFingerprint.keySet().removeAll(excludes);
    return Util.sort(classNameToFingerprint.values().toArray(new ClassFingerprint[classNameToFingerprint.size()]));
  }

  ClassFingerprint fingerprint(final ClassLoader classLoader, final String resourcePath) throws IOException {
    try (final InputStream in = classLoader.getResourceAsStream(resourcePath)) {
      new ClassReader(in).accept(this, 0);
      return className == null ? null : new ClassFingerprint(className, superClass, Util.sort(constructors.size() == 0 ? null : constructors.toArray(new ConstructorFingerprint[constructors.size()])), Util.sort(methods.size() == 0 ? null : methods.toArray(new MethodFingerprint[methods.size()])), Util.sort(fields.size() == 0 ? null : fields.toArray(new FieldFingerprint[fields.size()])));
    }
    finally {
      className = null;
      superClass = null;
      constructors.clear();
      methods.clear();
      fields.clear();
    }
  }

  @Override
  public void visit(final int version, final int access, final String name, final String signature, final String superName, final String[] interfaces) {
    if (!Modifier.isInterface(access) && !isSynthetic(access) && !Modifier.isPrivate(access)) {
      className = Type.getObjectType(name).getClassName();
      superClass = "java/lang/Object".equals(superName) ? null : Type.getObjectType(superName).getClassName();
    }

    super.visit(version, access, name, signature, superName, interfaces);
  }

  @Override
  public void visitInnerClass(final String name, final String outerName, final String innerName, final int access) {
    if (Visibility.get(access) == Visibility.PRIVATE || isSynthetic(access))
      excludes.add(Type.getObjectType(name).getClassName());

    super.visitInnerClass(name, outerName, innerName, access);
  }

  @Override
  public MethodVisitor visitMethod(final int access, final String name, final String desc, final String signature, final String[] exceptions) {
    final Type[] argumentTypes = Type.getArgumentTypes(desc);
    final String[] parameterTypes = argumentTypes.length == 0 ? null : new String[argumentTypes.length];
    for (int i = 0; i < argumentTypes.length; ++i)
      parameterTypes[i] = argumentTypes[i].getClassName();

    final String[] exceptionTypes = exceptions == null || exceptions.length == 0 ? null : new String[exceptions.length];
    if (exceptions != null)
      for (int i = 0; i < exceptions.length; ++i)
        exceptionTypes[i] = Type.getObjectType(exceptions[i]).getClassName();

    if (Visibility.get(access) != Visibility.PRIVATE && !isSynthetic(access)) {
      if ("<init>".equals(name)) {
        constructors.add(new ConstructorFingerprint(parameterTypes, Util.sort(exceptionTypes)));
      }
      else if (!"<clinit>".equals(name)) {
        final String returnType = Type.getMethodType(desc).getReturnType().getClassName();
        methods.add(new MethodFingerprint(name, "void".equals(returnType) ? null : returnType, parameterTypes, Util.sort(exceptionTypes)));
      }
    }

    return super.visitMethod(access, name, desc, signature, exceptions);
  }

  @Override
  public FieldVisitor visitField(final int access, final String name, final String desc, final String signature, final Object value) {
    if (Visibility.get(access) != Visibility.PRIVATE && !isSynthetic(access))
      fields.add(new FieldFingerprint(name, Type.getType(desc).getClassName()));

    return super.visitField(access, name, desc, signature, value);
  }
}