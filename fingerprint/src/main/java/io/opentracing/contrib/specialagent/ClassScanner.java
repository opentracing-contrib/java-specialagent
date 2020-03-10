/* Copyright 2019 The OpenTracing Authors
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

class ClassScanner extends ClassVisitor {
  private static final Logger logger = Logger.getLogger(ClassScanner.class);

  static ClassFingerprint fingerprint(final ClassLoader classLoader, final String resourcePath, final Set<String> innerClassExcludes) throws IOException {
    final Collection<MethodFingerprint> methods = new LinkedHashSet<>();
    final List<FieldFingerprint> fields = new ArrayList<>();
    final ClassScanner scanner = scan(classLoader, resourcePath, methods, fields, innerClassExcludes);
    return scanner == null ? null : new ClassFingerprint(scanner.className, scanner.superClass, scanner.constructors, new ArrayList<>(methods), fields);
  }

  private static ClassScanner scan(final ClassLoader classLoader, final String resourcePath, final Collection<MethodFingerprint> methods, final List<FieldFingerprint> fields, final Set<String> innerClassExcludes) throws IOException {
    final ClassScanner scanner = new ClassScanner(classLoader, methods, fields, innerClassExcludes);
    try (final InputStream in = classLoader.getResourceAsStream(resourcePath)) {
      new ClassReader(in).accept(scanner, 0);
      scanner.scanSupers();
      return scanner;
    }
    catch (final Exception e) {
      if (logger.isLoggable(Level.FINE))
        logger.warning((e.getMessage() != null ? e.getMessage() + ": " : "") + resourcePath);

      if (e instanceof IOException && !"Class not found".equals(e.getMessage()))
        throw e;

      return null;
    }
  }

  private static void scanInterfaces(final List<String> interfaces, final ClassLoader classLoader, final Collection<MethodFingerprint> methods, final List<FieldFingerprint> fields, final Set<String> innerClassExcludes) throws IOException {
    for (final String cls : interfaces) {
      if (!FingerprintUtil.isExcluded(cls)) {
        final ClassScanner scanner = ClassScanner.scan(classLoader, AssembleUtil.classNameToResource(cls), methods, fields, innerClassExcludes);
        if (scanner != null && scanner.interfaces != null)
          scanInterfaces(scanner.interfaces, classLoader, methods, fields, innerClassExcludes);
      }
    }
  }

  private final List<ConstructorFingerprint> constructors = new ArrayList<>();
  private final Collection<MethodFingerprint> methods;
  private final List<FieldFingerprint> fields;

  private String className;
  private String superClass;
  private List<String> interfaces;
  private final ClassLoader classLoader;
  private final Set<String> innerClassExcludes;

  private ClassScanner(final ClassLoader classLoader, final Collection<MethodFingerprint> methods, final List<FieldFingerprint> fields, final Set<String> innerClassExcludes) {
    super(Opcodes.ASM5);
    this.classLoader = classLoader;
    this.methods = methods;
    this.fields = fields;
    this.innerClassExcludes = innerClassExcludes;
  }

  private void scanSupers() throws IOException {
    String superClass = this.superClass;
    while (superClass != null) {
      final ClassScanner next = ClassScanner.scan(classLoader, AssembleUtil.classNameToResource(superClass), methods, fields, innerClassExcludes);
      superClass = next == null ? null : next.superClass;
    }

    if (interfaces != null)
      scanInterfaces(interfaces, classLoader, methods, fields, innerClassExcludes);
  }

  @Override
  public void visit(final int version, final int access, final String name, final String signature, final String superName, final String[] interfaces) {
    super.visit(version, access, name, signature, superName, interfaces);
    if (FingerprintUtil.isSynthetic(access) || Modifier.isPrivate(access))
      return;

    this.className = Type.getObjectType(name).getClassName();
    if (interfaces != null && interfaces.length != 0) {
      for (int i = 0; i < interfaces.length; ++i)
        interfaces[i] = interfaces[i].replace('/', '.');

      this.interfaces = new ArrayList<>();
      for (int i = 0; i < interfaces.length; ++i)
        this.interfaces.add(interfaces[i]);
    }

    this.superClass = superName == null || "java/lang/Object".equals(superName) ? null : Type.getObjectType(superName).getClassName();
  }

  @Override
  public void visitInnerClass(final String name, final String outerName, final String innerName, final int access) {
    super.visitInnerClass(name, outerName, innerName, access);
    if (Visibility.get(access) == Visibility.PRIVATE || FingerprintUtil.isSynthetic(access))
      innerClassExcludes.add(Type.getObjectType(name).getClassName());
  }

  @Override
  public MethodVisitor visitMethod(final int access, final String name, final String desc, final String signature, final String[] exceptions) {
    final MethodVisitor methodVisitor =  super.visitMethod(access, name, desc, signature, exceptions);
    if (Visibility.get(access) == Visibility.PRIVATE || FingerprintUtil.isSynthetic(access) || "<clinit>".equals(name))
      return null;

    final Type[] argumentTypes = Type.getArgumentTypes(desc);
    final List<String> parameterTypes = argumentTypes.length == 0 ? null : new ArrayList<String>(argumentTypes.length);
    for (int i = 0; i < argumentTypes.length; ++i)
      parameterTypes.add(argumentTypes[i].getClassName());

    final List<String> exceptionTypes = exceptions == null || exceptions.length == 0 ? null : new ArrayList<String>(exceptions.length);
    if (exceptions != null)
      for (int i = 0; i < exceptions.length; ++i)
        exceptionTypes.add(Type.getObjectType(exceptions[i]).getClassName());

    if ("<init>".equals(name)) {
      constructors.add(new ConstructorFingerprint(parameterTypes, AssembleUtil.sort(exceptionTypes)));
    }
    else {
      final String returnType = Type.getMethodType(desc).getReturnType().getClassName();
      methods.add(new MethodFingerprint(name, "void".equals(returnType) ? null : returnType, parameterTypes, AssembleUtil.sort(exceptionTypes)));
    }

    return methodVisitor;
  }

  @Override
  public FieldVisitor visitField(final int access, final String name, final String desc, final String signature, final Object value) {
    final FieldVisitor fieldVisitor = super.visitField(access, name, desc, signature, value);
    if (Visibility.get(access) != Visibility.PRIVATE && !FingerprintUtil.isSynthetic(access))
      fields.add(new FieldFingerprint(name, Type.getType(desc).getClassName()));

    return fieldVisitor;
  }
}