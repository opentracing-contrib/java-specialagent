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
import java.util.Objects;

class MethodLog extends Log {
  final String methodName;
  final String returnType;
  final List<String> parameterTypes;
  private List<String> exceptionTypes;

  MethodLog(final Phase phase, final String className, final String methodName, final String returnType, final List<String> parameterTypes) {
    super(phase, className);
    this.methodName = Objects.requireNonNull(methodName);
    this.returnType = returnType;
    this.parameterTypes = parameterTypes;
  }

  List<String> getExceptionTypes() {
    return this.exceptionTypes;
  }

  void setExceptionTypes(List<String> exceptionTypes) {
    this.exceptionTypes = exceptionTypes;
  }

  void resolve(final List<String> exceptionTypes) {
    this.exceptionTypes = exceptionTypes;
    resolve();
  }

  @Override
  public int hashCode() {
    int hashCode = className.hashCode() ^ methodName.hashCode();
    if (returnType != null)
      hashCode = hashCode * 37 + returnType.hashCode();

    if (parameterTypes != null)
      hashCode = hashCode * 37 + parameterTypes.hashCode();

    return hashCode;
  }

  @Override
  public boolean equals(final Object obj) {
    if (this == obj)
      return true;

    if (!(obj instanceof MethodLog))
      return false;

    final MethodLog that = (MethodLog)obj;
    return className.equals(that.className) && methodName.equals(that.methodName) && (returnType != null ? returnType.equals(that.returnType) : that.returnType == null) && (parameterTypes != null ? parameterTypes.equals(that.parameterTypes) : that.parameterTypes == null);
  }

  @Override
  public String toString() {
    final StringBuilder builder = new StringBuilder(super.toString());
    builder.append('#').append(methodName);
    builder.append('(');
    if (parameterTypes != null) {
      for (int i = 0; i < parameterTypes.size(); ++i) {
        if (i > 0)
          builder.append(", ");

        builder.append(parameterTypes.get(i));
      }
    }

    if (exceptionTypes != null) {
      builder.append(" throws ");
      for (int i = 0; i < exceptionTypes.size(); ++i) {
        if (i > 0)
          builder.append(", ");

        builder.append(exceptionTypes.get(i));
      }
    }

    builder.append("):");
    builder.append(returnType != null ? returnType :"void");
    return builder.toString();
  }
}