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

import org.objectweb.asm.AnnotationVisitor;

class LoggingAnnotationVisitor extends AnnotationVisitor {
  LoggingAnnotationVisitor(final int api) {
    super(api);
  }

  @Override
  public void visit(final String name, final Object value) {
    System.err.println("AnnotationVisitor#visit(\"" + name + "\", \"" + value + "\")");
    super.visit(name, value);
  }

  @Override
  public void visitEnum(final String name, final String desc, final String value) {
    System.err.println("AnnotationVisitor#visitEnum(\"" + name + "\", \"" + desc + "\", \"" + value + "\")");
    super.visitEnum(name, desc, value);
  }

  @Override
  public AnnotationVisitor visitAnnotation(final String name, final String desc) {
    System.err.println("AnnotationVisitor#visitAnnotation(\"" + name + "\", \"" + desc + "\")");
    return super.visitAnnotation(name, desc);
  }

  @Override
  public AnnotationVisitor visitArray(final String name) {
    System.err.println("AnnotationVisitor#visitArray(\"" + name + ")");
    return super.visitArray(name);
  }

  @Override
  public void visitEnd() {
    super.visitEnd();
  }
}