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
import org.objectweb.asm.Attribute;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.TypePath;

class LoggingFieldVisitor extends FieldVisitor {
  private final String name;

  LoggingFieldVisitor(final int api, final String name) {
    super(api);
    this.name = name;
  }

  @Override
  public AnnotationVisitor visitAnnotation(final String desc, final boolean visible) {
    System.err.println("FieldVisitor[\"" + this.name + "\"]#visitAnnotation(\"" + desc + "\", " + visible + ")");
    return new LoggingAnnotationVisitor(api);
  }

  @Override
  public AnnotationVisitor visitTypeAnnotation(final int typeRef, final TypePath typePath, final String desc, final boolean visible) {
    System.err.println("FieldVisitor[\"" + this.name + "\"]#visitTypeAnnotation(" + typeRef + ", \"" + typePath + "\", \"" + desc + "\", " + visible + ")");
    return new LoggingAnnotationVisitor(api);
  }

  @Override
  public void visitAttribute(final Attribute attr) {
    System.err.println("FieldVisitor[\"" + this.name + "\"]#visitAttribute(\"" + attr + "\")");
    super.visitAttribute(attr);
  }

  @Override
  public void visitEnd() {
    System.err.println("FieldVisitor[\"" + this.name + "\"]#visitEnd()");
    super.visitEnd();
  }
}