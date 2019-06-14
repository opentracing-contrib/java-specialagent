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
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.TypePath;

class LoggingMethodVisitor extends MethodVisitor {
  private final String name;

  LoggingMethodVisitor(final int api, final String name) {
    super(api);
    this.name = name;
  }

  @Override
  public void visitParameter(final String name, final int access) {
    System.err.println("MethodVisitor[\"" + this.name + "\"]#visitParameter(\"" + name + "\", " + access + ")");
    super.visitParameter(name, access);
  }

  @Override
  public AnnotationVisitor visitAnnotationDefault() {
    System.err.println("MethodVisitor[\"" + this.name + "\"]#visitAnnotationDefault()");
    return new LoggingAnnotationVisitor(api);
  }

  @Override
  public AnnotationVisitor visitAnnotation(final String desc, final boolean visible) {
    System.err.println("MethodVisitor[\"" + this.name + "\"]#visitAnnotation(\"" + desc + "\", " + visible + ")");
    return new LoggingAnnotationVisitor(api);
  }

  @Override
  public AnnotationVisitor visitTypeAnnotation(final int typeRef, final TypePath typePath, final String desc, final boolean visible) {
    System.err.println("MethodVisitor[\"" + this.name + "\"]#visitTypeAnnotation(" + typeRef + ", \"" + typePath + "\", \"" + desc + "\", " + visible + ")");
    return new LoggingAnnotationVisitor(api);
  }

  @Override
  public AnnotationVisitor visitParameterAnnotation(final int parameter, final String desc, final boolean visible) {
    System.err.println("MethodVisitor[\"" + this.name + "\"]#visitParameterAnnotation(" + parameter + ", \"" + desc + "\", " + visible + ")");
    return new LoggingAnnotationVisitor(api);
  }

  @Override
  public void visitAttribute(final Attribute attr) {
    System.err.println("MethodVisitor[\"" + this.name + "\"]#visitAttribute(\"" + attr + "\")");
    super.visitAttribute(attr);
  }

  @Override
  public void visitCode() {
    System.err.println("MethodVisitor[\"" + this.name + "\"]#visitCode()");
    super.visitCode();
  }

  @Override
  public void visitFrame(final int type, final int nLocal, final Object[] local, final int nStack, final Object[] stack) {
    System.err.println("MethodVisitor[\"" + this.name + "\"]#visitFrame(" + type + ", " + nLocal + ", \"" + Arrays.toString(local) + "\", " + nStack + ", \"" + Arrays.toString(stack) + "\")");
    super.visitFrame(type, nLocal, local, nStack, stack);
  }

  @Override
  public void visitInsn(final int opcode) {
    System.err.println("MethodVisitor[\"" + this.name + "\"]#visitInsn(" + opcode + ")");
    super.visitInsn(opcode);
  }

  @Override
  public void visitIntInsn(final int opcode, final int operand) {
    System.err.println("MethodVisitor[\"" + this.name + "\"]#visitIntInsn(" + opcode + ", " + operand + ")");
    super.visitIntInsn(opcode, operand);
  }

  @Override
  public void visitVarInsn(final int opcode, final int var) {
    System.err.println("MethodVisitor[\"" + this.name + "\"]#visitVarInsn(" + opcode + ", " + var + ")");
    super.visitVarInsn(opcode, var);
  }

  @Override
  public void visitTypeInsn(final int opcode, final String type) {
    System.err.println("MethodVisitor[\"" + this.name + "\"]#visitTypeInsn(" + opcode + ", \"" + type + "\")");
    super.visitTypeInsn(opcode, type);
  }

  @Override
  public void visitFieldInsn(final int opcode, final String owner, final String name, final String desc) {
    System.err.println("MethodVisitor[\"" + this.name + "\"]#visitFieldInsn(" + opcode + ", \"" + owner + "\", \"" + name + "\", \"" + desc + "\")");
    super.visitFieldInsn(opcode, owner, name, desc);
  }

  @Override
  @Deprecated
  public void visitMethodInsn(final int opcode, final String owner, final String name, final String desc) {
    System.err.println("MethodVisitor[\"" + this.name + "\"]#visitMethodInsn(" + opcode + ", \"" + owner + "\", \"" + name + "\", \"" + desc + "\")");
    super.visitMethodInsn(opcode, owner, name, desc);
  }

  @Override
  public void visitMethodInsn(final int opcode, final String owner, final String name, final String desc, final boolean itf) {
    System.err.println("MethodVisitor[\"" + this.name + "\"]#visitMethodInsn(" + opcode + ", \"" + owner + "\", \"" + name + "\", \"" + desc + "\", " + itf + ")");
    super.visitMethodInsn(opcode, owner, name, desc, itf);
  }

  @Override
  public void visitInvokeDynamicInsn(final String name, final String desc, final Handle bsm, final Object ... bsmArgs) {
    System.err.println("MethodVisitor[\"" + this.name + "\"]#visitInvokeDynamicInsn(\"" + name + "\", \"" + desc + "\", \"" + bsm + "\", \"" + Arrays.toString(bsmArgs) + "\")");
    super.visitInvokeDynamicInsn(name, desc, bsm, bsmArgs);
  }

  @Override
  public void visitJumpInsn(final int opcode, final Label label) {
    System.err.println("MethodVisitor[\"" + this.name + "\"]#visitJumpInsn(" + opcode + ", \"" + label + "\")");
    super.visitJumpInsn(opcode, label);
  }

  @Override
  public void visitLabel(final Label label) {
    System.err.println("MethodVisitor[\"" + this.name + "\"]#visitLabel(\"" + label + "\")");
    super.visitLabel(label);
  }

  @Override
  public void visitLdcInsn(final Object cst) {
    System.err.println("MethodVisitor[\"" + this.name + "\"]#visitLdcInsn(\"" + cst + "\")");
    super.visitLdcInsn(cst);
  }

  @Override
  public void visitIincInsn(final int var, final int increment) {
    System.err.println("MethodVisitor[\"" + this.name + "\"]#visitIincInsn(" + var + ", \"" + increment + "\")");
    super.visitIincInsn(var, increment);
  }

  @Override
  public void visitTableSwitchInsn(final int min, final int max, final Label dflt, final Label ... labels) {
    System.err.println("MethodVisitor[\"" + this.name + "\"]#visitTableSwitchInsn(" + min + ", " + max + ", \"" + dflt + "\", \"" + Arrays.toString(labels) + "\")");
    super.visitTableSwitchInsn(min, max, dflt, labels);
  }

  @Override
  public void visitLookupSwitchInsn(final Label dflt, final int[] keys, final Label[] labels) {
    System.err.println("MethodVisitor[\"" + this.name + "\"]#visitLookupSwitchInsn(\"" + dflt + "\", " + Arrays.toString(keys) + ", \"" + Arrays.toString(labels) + "\")");
    super.visitLookupSwitchInsn(dflt, keys, labels);
  }

  @Override
  public void visitMultiANewArrayInsn(final String desc, final int dims) {
    System.err.println("MethodVisitor[\"" + this.name + "\"]#visitMultiANewArrayInsn(\"" + desc + "\", " + dims + ")");
    super.visitMultiANewArrayInsn(desc, dims);
  }

  @Override
  public AnnotationVisitor visitInsnAnnotation(final int typeRef, final TypePath typePath, final String desc, final boolean visible) {
    System.err.println("MethodVisitor[\"" + this.name + "\"]#visitInsnAnnotation(" + typeRef + ", \"" + typePath + "\", \"" + desc + "\", " + visible + ")");
    return new LoggingAnnotationVisitor(api);
  }

  @Override
  public void visitTryCatchBlock(final Label start, final Label end, final Label handler, final String type) {
    System.err.println("MethodVisitor[\"" + this.name + "\"]#visitTryCatchBlock(\"" + start + "\", \"" + end + "\", \"" + handler + "\", \"" + type + "\")");
    super.visitTryCatchBlock(start, end, handler, type);
  }

  @Override
  public AnnotationVisitor visitTryCatchAnnotation(final int typeRef, final TypePath typePath, final String desc, final boolean visible) {
    System.err.println("MethodVisitor[\"" + this.name + "\"]#visitTryCatchAnnotation(" + typeRef + ", \"" + typePath + "\", \"" + desc + "\", " + visible + ")");
    return new LoggingAnnotationVisitor(api);
  }

  @Override
  public void visitLocalVariable(final String name, final String desc, final String signature, final Label start, final Label end, final int index) {
    System.err.println("MethodVisitor[\"" + this.name + "\"]#visitLocalVariable(\"" + name + "\", \"" + desc + "\", \"" + signature + "\", \"" + start + "\", \"" + end + "\", " + index + ")");
    super.visitLocalVariable(name, desc, signature, start, end, index);
  }

  @Override
  public AnnotationVisitor visitLocalVariableAnnotation(final int typeRef, final TypePath typePath, final Label[] start, final Label[] end, final int[] index, final String desc, final boolean visible) {
    System.err.println("MethodVisitor[\"" + this.name + "\"]#visitLocalVariableAnnotation(" + typeRef + ", \"" + typePath + "\", \"" + Arrays.toString(start) + "\", \"" + Arrays.toString(end) + "\", " + Arrays.toString(index) + ", " + index + ", \"" + desc + "\", " + visible + ")");
    return new LoggingAnnotationVisitor(api);
  }

  @Override
  public void visitLineNumber(final int line, final Label start) {
    System.err.println("MethodVisitor[\"" + this.name + "\"]#visitLineNumber(" + line + ", \"" + start + "\")");
    super.visitLineNumber(line, start);
  }

  @Override
  public void visitMaxs(final int maxStack, final int maxLocals) {
    System.err.println("MethodVisitor[\"" + this.name + "\"]#visitMaxs(" + maxStack + ", " + maxLocals + ")");
    super.visitMaxs(maxStack, maxLocals);
  }

  @Override
  public void visitEnd() {
    System.err.println("MethodVisitor[\"" + this.name + "\"]#visitEnd()");
    super.visitEnd();
  }
}