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

import java.util.List;

class ClassLog extends Log {
  private String superClass;
  private List<String> interfaces;

  ClassLog(final String className) {
    super(className);
  }

  String getSuperClass() {
    return this.superClass;
  }

  List<String> getInterfaces() {
    return this.interfaces;
  }

  void resolve(final String superClass, final List<String> interfaces) {
    this.superClass = superClass;
    this.interfaces = interfaces;
    resolve();
  }

  @Override
  public int hashCode() {
    return getClassName().hashCode();
  }

  @Override
  public boolean equals(final Object obj) {
    if (this == obj)
      return true;

    if (!(obj instanceof ClassLog))
      return false;

    final ClassLog that = (ClassLog)obj;
    return getClassName().equals(that.getClassName());
  }

  @Override
  public String toString() {
    final StringBuilder builder = new StringBuilder(super.toString());
    if (superClass != null)
      builder.append(" extends ").append(superClass);

    if (interfaces != null) {
      builder.append(" implements ");
      for (int i = 0; i < interfaces.size(); ++i) {
        if (i > 0)
          builder.append(',');

        builder.append(interfaces.get(i));
      }
    }

    return builder.toString();
  }
}