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

import java.util.List;

/**
 * A {@link Fingerprint} that represents the fingerprint of a {@code Method}.
 *
 * @author Seva Safris
 */
class MethodFingerprint extends NamedFingerprint<MethodFingerprint> {
  private static final long serialVersionUID = -6005870987922050364L;

  private final String returnType;
  private final List<String> parameterTypes;
  private final List<String> exceptionTypes;

  /**
   * Creates a new {@code MethodFingerprint} for the specified {@code Method}.
   *
   * @param name The name of the method.
   * @param returnType The class name of the return type.
   * @param parameterTypes The array of class names in the parameter signature,
   *          or {@code null} if there are no parameters.
   * @param exceptionTypes The sorted array of class names in the exception
   *          signature, or {@code null} if there are no exceptions.
   */
  MethodFingerprint(final String name, final String returnType, final List<String> parameterTypes, final List<String> exceptionTypes) {
    super(name);
    this.returnType = returnType;
    this.parameterTypes = AssembleUtil.sort(parameterTypes);
    this.exceptionTypes = AssembleUtil.sort(exceptionTypes);
  }

  /**
   * @return The name of the return type.
   */
  String getReturnType() {
    return this.returnType;
  }

  /**
   * @return The names of the parameter types.
   */
  List<String> getParameterTypes() {
    return this.parameterTypes;
  }

  /**
   * @return The names of the exception types.
   */
  List<String> getExceptionTypes() {
    return this.exceptionTypes;
  }

  @Override
  public int compareTo(final MethodFingerprint o) {
    int comparison = super.compareTo(o);
    if (comparison != 0)
      return comparison;

    comparison = AssembleUtil.compare(parameterTypes, o.parameterTypes);
    if (comparison != 0)
      return comparison;

    return 0; // Util.compare(exceptionTypes, o.exceptionTypes);
  }

  @Override
  public boolean equals(final Object obj) {
    if (obj == this)
      return true;

    if (!(obj instanceof MethodFingerprint))
      return false;

    final MethodFingerprint that = (MethodFingerprint)obj;
    if (!getName().equals(that.getName()))
      return false;

    if (returnType == null ? that.returnType != null : !returnType.equals(that.returnType))
      return false;

    if (parameterTypes == null ? that.parameterTypes != null : !parameterTypes.equals(that.parameterTypes))
      return false;

//    if (exceptionTypes == null ? that.exceptionTypes != null : !exceptionTypes.equals(that.exceptionTypes))
//      return false;

    return true;
  }

  @Override
  public int hashCode() {
    int hashCode = 0;
    hashCode = hashCode * 37 + getName().hashCode();
    hashCode = hashCode * 37 + (returnType == null ? 0 : returnType.hashCode());
    hashCode = hashCode * 37 + (parameterTypes == null ? 0 : parameterTypes.hashCode());
//    hashCode = hashCode * 37 + (exceptionTypes == null ? 0 : exceptionTypes.hashCode());
    return hashCode;
  }

  @Override
  public String toString() {
    final StringBuilder builder = new StringBuilder();
    builder.append(returnType == null ? "void" : returnType).append(' ');
    builder.append(getName()).append("(");
    if (parameterTypes != null)
      builder.append(AssembleUtil.toString(parameterTypes, ", "));

    builder.append(')');
    if (exceptionTypes != null)
      builder.append(" throws ").append(AssembleUtil.toString(exceptionTypes, ", "));

    builder.append(';');
    return builder.toString();
  }
}