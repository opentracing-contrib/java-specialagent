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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.TypePath;
import org.objectweb.asm.signature.SignatureReader;
import org.objectweb.asm.signature.SignatureVisitor;

class Fingerprinter extends ClassVisitor {
  private static final Logger logger = Logger.getLogger(Fingerprinter.class.getName());
  private static final Pattern synthetic = Pattern.compile("access\\$\\d+");

  private final Set<String> innerClassExcludes = new HashSet<>();
  private final ClassLoader classLoader;
  private final LogSet logs;
  private final SignatureVisitor signatureVisitor;

  private String superClass;
  private String[] interfaces;

  Fingerprinter(final ClassLoader classLoader, final LogSet logs, final boolean debug) {
    super(Opcodes.ASM5, debug ? new LoggingClassVisitor(Opcodes.ASM5) : null);
    this.classLoader = classLoader;
    this.logs = logs;
    this.signatureVisitor = debug ? new LoggingSignatureVisitor(api) {
      @Override
      public void visitClassType(final String name) {
        addClassRef(Type.getObjectType(name), Phase.NONE);
      }
    } : new SignatureVisitor(api) {
      @Override
      public void visitClassType(final String name) {
        addClassRef(Type.getObjectType(name), Phase.NONE);
      }
    };
  }

  boolean fingerprint(final Phase phase, final String resourcePath) throws IOException {
    this.phase = phase;
    this.filtering = false;
    try (final InputStream in = classLoader.getResourceAsStream(resourcePath)) {
      new ClassReader(in).accept(this, 0);
      return true;
    }
    catch (final Exception e) {
      if (logger.isLoggable(Level.FINE))
        logger.log(Level.FINE, (e.getMessage() != null ? e.getMessage() + ": " : "") + resourcePath, e);

      if (e instanceof IOException && !"Class not found".equals(e.getMessage()))
        throw e;

      return false;
    }
  }

  void compass(final int depth) throws IOException {
    filtering = true;
    for (int i = 0; logs.compass(this) && i < depth; ++i);
  }

  private String className;

  private boolean filtering = false;
  private Phase phase;

  private static boolean isPrimitive(final Type type) {
    if (type.getSort() == Type.ARRAY)
      throw new IllegalArgumentException("Type cannot be an array type");

    final String name = type.getClassName();
    return "boolean".equals(name) || "byte".equals(name) || "char".equals(name) || "short".equals(name) || "int".equals(name) || "long".equals(name) || "float".equals(name) || "double".equals(name);
  }

  private static String typeToClassName(Type type, final boolean withExcludes) {
    if (withExcludes && type.getSort() != Type.OBJECT)
      return null;

    if (type.getSort() == Type.ARRAY) {
      final String className = type.getClassName();
      type = Type.getObjectType(className.substring(0, className.length() - 2));
    }

    if (withExcludes && isPrimitive(type))
      return null;

    final String className = type.getClassName();
    return withExcludes && FingerprintUtil.isExcluded(className) ? null : className;
  }

  private ClassLog addClassRef(final Type type, final Phase lifecyclePhase) {
    final String className = typeToClassName(type, true);
    return className == null ? null : logs.add(new ClassLog(lifecyclePhase, className));
  }

  private FieldLog addFieldRef(final Type type, final String name, final Phase phase) {
    final String className = typeToClassName(type, true);
    return className == null ? null : logs.add(new FieldLog(phase, className, name));
  }

  private static MethodLog makeMethodCall(final String className, final String name, final Type methodDescriptor, final Phase lifecyclePhase) {
    final Type[] argumentTypes = methodDescriptor.getArgumentTypes();
    final List<String> parameterTypes = argumentTypes.length == 0 ? null : new ArrayList<String>(argumentTypes.length);
    for (int i = 0; i < argumentTypes.length; ++i)
      parameterTypes.add(argumentTypes[i].getClassName());

    return new MethodLog(lifecyclePhase, className, name, methodDescriptor.getReturnType().getClassName(), parameterTypes);
  }

  @Override
  public void visit(final int version, final int access, final String name, final String signature, final String superName, final String[] interfaces) {
    if (FingerprintUtil.isSynthetic(access))
      return;

    super.visit(version, access, name, signature, superName, interfaces);
    if (signature != null)
      new SignatureReader(signature).accept(signatureVisitor);

    final Type type = Type.getObjectType(name);
    this.className = type.getClassName();

    final ClassLog log = addClassRef(type, Phase.LOAD);
    if (log == null || log.isResolved())
      return;

    if (superName == null || "java/lang/Object".equals(superName)) {
      this.superClass = null;
    }
    else {
      final Type superType = Type.getObjectType(superName);
      addClassRef(superType, Phase.LOAD);
      this.superClass = superType.getClassName();
    }

    if (interfaces == null || interfaces.length == 0) {
      this.interfaces = null;
    }
    else {
      this.interfaces = interfaces;
      for (int i = 0; i < interfaces.length; ++i) {
        final Type cls = Type.getObjectType(interfaces[i]);
        interfaces[i] = cls.getClassName();
        addClassRef(cls, Phase.LOAD);
      }
    }

    log.resolve(this.superClass, this.interfaces);
  }

  @Override
  public void visitInnerClass(final String name, final String outerName, final String innerName, final int access) {
    super.visitInnerClass(name, outerName, innerName, access);
    if (FingerprintUtil.isSynthetic(access))
      innerClassExcludes.add(Type.getObjectType(name).getClassName());
    else if (!name.startsWith(className.replace('.', '/') + "$"))
      addClassRef(Type.getObjectType(name), Phase.NONE);
  }

  @Override
  public FieldVisitor visitField(final int access, final String name, final String desc, final String signature, final Object value) {
    if (FingerprintUtil.isPrivate(access) || FingerprintUtil.isSynthetic(access))
      return null;

    final FieldVisitor fieldVisitor = super.visitField(access, name, desc, signature, value);
    final Type type = Type.getType(desc);
    addClassRef(type, filtering ? Phase.NONE : Phase.CALL);
    final FieldLog log = addFieldRef(Type.getObjectType(className), name, filtering ? Phase.NONE : Phase.CALL);
    if (log == null)
      return null;

    if (signature != null)
      new SignatureReader(signature).accept(signatureVisitor);

    log.resolve(typeToClassName(type, false));
    return fieldVisitor;
  }

  @Override
  public AnnotationVisitor visitAnnotation(final String desc, final boolean visible) {
    final AnnotationVisitor annotationVisitor = super.visitAnnotation(desc, visible);
    addClassRef(Type.getType(desc), Phase.NONE);
    return annotationVisitor;
  }

  @Override
  public AnnotationVisitor visitTypeAnnotation(final int typeRef, final TypePath typePath, final String desc, final boolean visible) {
    final AnnotationVisitor annotationVisitor = super.visitTypeAnnotation(typeRef, typePath, desc, visible);
    addClassRef(Type.getType(desc), Phase.NONE);
    return annotationVisitor;
  }

  private Phase getPhase(final boolean condition) {
    return condition ? (filtering ? Phase.NONE : phase) : (filtering ? phase : Phase.CALL);
  }

  @Override
  public MethodVisitor visitMethod(final int access, final String name, final String desc, final String signature, final String[] exceptions) {
    final MethodVisitor methodVisitor = super.visitMethod(access, name, desc, signature, exceptions);
    if (FingerprintUtil.isSynthetic(access) || FingerprintUtil.isPrivate(access))
      return null;

    if (!FingerprintUtil.isExcluded(className) && !"<clinit>".equals(name)) {
      final Type methodType = Type.getMethodType(desc);
      final Type[] argumentTypes = methodType.getArgumentTypes();
      final String[] parameterTypes = argumentTypes.length == 0 ? null : new String[argumentTypes.length];
      for (int i = 0; i < argumentTypes.length; ++i)
        parameterTypes[i] = argumentTypes[i].getClassName();

      final MethodLog log = logs.add(makeMethodCall(className, name, methodType, filtering ? Phase.NONE : Phase.CALL));
      if (filtering && !logs.contains(log))
        return null;

      final List<String> exceptionTypes = exceptions == null || exceptions.length == 0 ? null : new ArrayList<String>(exceptions.length);
      if (exceptions != null) {
        for (int i = 0; i < exceptions.length; ++i) {
          final Type type = Type.getObjectType(exceptions[i]);
          addClassRef(type, filtering ? Phase.NONE : Phase.CALL);
          exceptionTypes.add(type.getClassName());
        }
      }

      if (signature != null)
        new SignatureReader(signature).accept(signatureVisitor);

      log.resolve(exceptionTypes);
    }

    return new MethodVisitor(api, methodVisitor) {
      @Override
      public AnnotationVisitor visitAnnotation(final String desc, final boolean visible) {
        final AnnotationVisitor annotationVisitor = super.visitAnnotation(desc, visible);
        addClassRef(Type.getType(desc), Phase.NONE);
        return annotationVisitor;
      }

      @Override
      public void visitFieldInsn(final int opcode, final String owner, final String name, final String desc) {
        super.visitFieldInsn(opcode, owner, name, desc);
        if (!FingerprintUtil.isPutStatic(opcode))
          addFieldRef(Type.getObjectType(owner), name, getPhase(FingerprintUtil.isGetStatic(opcode)));
      }

      @Override
      public void visitMethodInsn(final int opcode, final String owner, final String name, final String desc, final boolean itf) {
        super.visitMethodInsn(opcode, owner, name, desc, itf);
        if (filtering)
          return;

        // Skip if INVOKESPECIAL, because the later scan via addClassRef(type..) will pick up the constructor
        // Skipping this avoids the virtual constructors of member inner classes
        if (synthetic.matcher(name).matches() || FingerprintUtil.isInvokeSpecial(opcode))
          return;

        final Type type = Type.getObjectType(owner);
        final String className = typeToClassName(type, true);
        if (className == null)
          return;

        addClassRef(type, phase);
        logs.add(makeMethodCall(className, name, Type.getMethodType(desc), getPhase(FingerprintUtil.isInvokeStatic(opcode))));
      }

      @Override
      public void visitTypeInsn(final int opcode, final String type) {
        super.visitTypeInsn(opcode, type);
        if (filtering)
          return;

        addClassRef(Type.getObjectType(type), filtering ? Phase.NONE : Phase.CALL);
      }

      @Override
      public void visitLocalVariable(final String name, final String desc, final String signature, final Label start, final Label end, final int index) {
        super.visitLocalVariable(name, desc, signature, start, end, index);
        addClassRef(Type.getType(desc), filtering ? Phase.NONE : Phase.CALL);
        if (signature != null)
          new SignatureReader(signature).accept(signatureVisitor);
      }
    };
  }

  @Override
  public void visitEnd() {
    super.visitEnd();
    logs.markAllResolved(this.className, true);
  }
}