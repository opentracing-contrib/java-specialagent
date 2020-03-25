/* Copyright 2019 The OpenTracing Authors
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

import java.util.Arrays;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Attribute;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.TypePath;

class LoggingClassVisitor extends ClassVisitor {
  private String name;

  LoggingClassVisitor(final int api) {
    super(api);
  }

  @Override
  public void visit(final int version, final int access, final String name, final String signature, final String superName, final String[] interfaces) {
    this.name = name;
    System.err.println("ClassVisitor[\"" + this.name + "\"]#visit(" + version + ", " + access + ", \"" + name + "\", \"" + signature + "\", \"" + superName + "\", \"" + Arrays.toString(interfaces) + "\")");
    super.visit(version, access, name, signature, superName, interfaces);
  }

  @Override
  public void visitSource(final String source, final String debug) {
    System.err.println("ClassVisitor[\"" + this.name + "\"]#visitSource(\"" + source + "\", \"" + debug + "\")");
    super.visitSource(source, debug);
  }

  @Override
  public void visitOuterClass(final String owner, final String name, final String desc) {
    System.err.println("ClassVisitor[\"" + this.name + "\"]#visitOuterClass(\"" + owner + "\", \"" + name + "\", \"" + name + "\", \"" + desc + "\")");
    super.visitOuterClass(owner, name, desc);
  }

  @Override
  public AnnotationVisitor visitAnnotation(final String desc, final boolean visible) {
    System.err.println("ClassVisitor[\"" + this.name + "\"]#visitAnnotation(\"" + desc + "\", " + visible + ")");
    return new LoggingAnnotationVisitor(api);
  }

  @Override
  public AnnotationVisitor visitTypeAnnotation(final int typeRef, final TypePath typePath, final String desc, final boolean visible) {
    System.err.println("ClassVisitor[\"" + this.name + "\"]#visitTypeAnnotation(\"" + typeRef + "\", \"" + typePath + "\", \"" + desc + "\", " + visible + ")");
    return new LoggingAnnotationVisitor(api);
  }

  @Override
  public void visitAttribute(final Attribute attr) {
    System.err.println("ClassVisitor[\"" + this.name + "\"]#visitAttribute(\"" + attr + "\")");
    super.visitAttribute(attr);
  }

  @Override
  public void visitInnerClass(final String name, final String outerName, final String innerName, final int access) {
    System.err.println("ClassVisitor[\"" + this.name + "\"]#visitInnerClass(\"" + name + "\", \"" + outerName + "\", \"" + innerName + "\", " + access + ")");
    super.visitInnerClass(name, outerName, innerName, access);
  }

  @Override
  public FieldVisitor visitField(final int access, final String name, final String desc, final String signature, final Object value) {
    System.err.println("ClassVisitor[\"" + this.name + "\"]#visitField(" + access + ", \"" + name + "\", \"" + desc + "\", \"" + signature + "\", \"" + value + "\")");
    return new LoggingFieldVisitor(api, name);
  }

  @Override
  public MethodVisitor visitMethod(final int access, final String name, final String desc, final String signature, final String[] exceptions) {
    System.err.println("ClassVisitor[\"" + this.name + "\"]#visitMethod(" + access + ", \"" + name + "\", \"" + desc + "\", \"" + signature + "\", \"" + Arrays.toString(exceptions) + "\")");
    return new LoggingMethodVisitor(api, name);
  }

  @Override
  public void visitEnd() {
    System.err.println("ClassVisitor[\"" + this.name + "\"]#visitEnd()");
    super.visitEnd();
  }
}