/* Copyright 2018 The OpenTracing Authors
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

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;

class MethodFingerprint extends NamedFingerprint<MethodFingerprint> {
  private static final long serialVersionUID = -6005870987922050364L;

  static MethodFingerprint[] recurse(final Method[] methods, final int index, final int depth) {
    for (int i = index; i < methods.length; ++i) {
      if (!methods[i].isSynthetic() && !Modifier.isPrivate(methods[i].getModifiers())) {
        final MethodFingerprint fingerprint = new MethodFingerprint(methods[i]);
        final MethodFingerprint[] fingerprints = recurse(methods, i + 1, depth + 1);
        fingerprints[depth] = fingerprint;
        return fingerprints;
      }
    }

    return depth == 0 ? null : new MethodFingerprint[depth];
  }

  private final String returnType;
  private final String[] parameterTypes;
  private final String[] exceptionTypes;

  MethodFingerprint(final Method method) {
    super(method.getName());
    this.returnType = Util.getName(method.getReturnType());
    this.parameterTypes = Util.getNames(method.getParameterTypes());
    this.exceptionTypes = Util.sort(Util.getNames(method.getExceptionTypes()));
  }

  public String getReturnType() {
    return this.returnType;
  }

  public String[] getParameterTypes() {
    return this.parameterTypes;
  }

  public String[] getExceptionTypes() {
    return this.exceptionTypes;
  }

  @Override
  public int compareTo(final MethodFingerprint o) {
    int comparison = super.compareTo(o);
    if (comparison != 0)
      return comparison;

    comparison = Arrays.compare(parameterTypes, o.parameterTypes);
    if (comparison != 0)
      return comparison;

    return Arrays.compare(exceptionTypes, o.exceptionTypes);
  }

  @Override
  public boolean equals(final Object obj) {
    if (obj == this)
      return true;

    if (!(obj instanceof MethodFingerprint))
      return false;

    final MethodFingerprint that = (MethodFingerprint)obj;
    if (returnType != null ? !returnType.equals(that.returnType) : that.returnType != null)
      return false;

    if (parameterTypes == null ? that.parameterTypes != null : that.parameterTypes == null || !Arrays.equals(parameterTypes, that.parameterTypes))
      return false;

    if (exceptionTypes == null ? that.exceptionTypes != null : that.exceptionTypes == null || !Arrays.equals(exceptionTypes, that.exceptionTypes))
      return false;

    return true;
  }

  @Override
  public String toString() {
    final StringBuilder builder = new StringBuilder();
    builder.append(returnType == null ? "void" : returnType).append(' ');
    builder.append(getName()).append("(");
    if (parameterTypes != null)
      builder.append(Util.toString(parameterTypes, ", "));

    builder.append(")");
    if (exceptionTypes != null)
      builder.append(" throws ").append(Util.toString(exceptionTypes, ", "));

    builder.append(";");
    return builder.toString();
  }
}