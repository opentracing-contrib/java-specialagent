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

import org.objectweb.asm.signature.SignatureVisitor;

class LoggingSignatureVisitor extends SignatureVisitor {
  LoggingSignatureVisitor(final int api) {
    super(api);
  }

  @Override
  public void visitFormalTypeParameter(final String name) {
    System.err.println("SignatureVisitor#visitFormalTypeParameter(\"" + name + "\")");
    super.visitFormalTypeParameter(name);
  }

  @Override
  public SignatureVisitor visitClassBound() {
    System.err.println("SignatureVisitor#visitClassBound()");
    return super.visitClassBound();
  }

  @Override
  public SignatureVisitor visitInterfaceBound() {
    System.err.println("SignatureVisitor#visitInterfaceBound()");
    return super.visitInterfaceBound();
  }

  @Override
  public SignatureVisitor visitSuperclass() {
    System.err.println("SignatureVisitor#visitSuperclass()");
    return super.visitSuperclass();
  }

  @Override
  public SignatureVisitor visitInterface() {
    System.err.println("SignatureVisitor#visitInterface()");
    return super.visitInterface();
  }

  @Override
  public SignatureVisitor visitParameterType() {
    System.err.println("SignatureVisitor#visitParameterType()");
    return super.visitParameterType();
  }

  @Override
  public SignatureVisitor visitReturnType() {
    System.err.println("SignatureVisitor#visitReturnType()");
    return super.visitReturnType();
  }

  @Override
  public SignatureVisitor visitExceptionType() {
    System.err.println("SignatureVisitor#visitExceptionType()");
    return super.visitExceptionType();
  }

  @Override
  public void visitBaseType(final char descriptor) {
    System.err.println("SignatureVisitor#visitBaseType('" + descriptor + "')");
    super.visitBaseType(descriptor);
  }

  @Override
  public void visitTypeVariable(final String name) {
    System.err.println("SignatureVisitor#visitTypeVariable(\"" + name + "\")");
    super.visitTypeVariable(name);
  }

  @Override
  public SignatureVisitor visitArrayType() {
    System.err.println("SignatureVisitor#visitArrayType()");
    return super.visitArrayType();
  }

  @Override
  public void visitClassType(final String name) {
    System.err.println("SignatureVisitor#visitClassType(\"" + name + "\")");
    super.visitClassType(name);
  }

  @Override
  public void visitInnerClassType(final String name) {
    System.err.println("SignatureVisitor#visitInnerClassType(\"" + name + "\")");
    super.visitInnerClassType(name);
  }

  @Override
  public void visitTypeArgument() {
    System.err.println("SignatureVisitor#visitTypeArgument()");
    super.visitTypeArgument();
  }

  @Override
  public SignatureVisitor visitTypeArgument(final char wildcard) {
    System.err.println("SignatureVisitor#visitTypeArgument('" + wildcard + "')");
    return super.visitTypeArgument(wildcard);
  }

  @Override
  public void visitEnd() {
    System.err.println("SignatureVisitor#visitEnd()");
    super.visitEnd();
  }
}