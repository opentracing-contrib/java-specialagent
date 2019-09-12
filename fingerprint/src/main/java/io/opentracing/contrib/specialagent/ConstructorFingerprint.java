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
 * A {@link Fingerprint} that represents the fingerprint of a
 * {@code Constructor}.
 *
 * @author Seva Safris
 */
class ConstructorFingerprint extends Fingerprint implements Comparable<ConstructorFingerprint> {
  private static final long serialVersionUID = -6005870987922050364L;

  private final List<String> parameterTypes;
  private final List<String> exceptionTypes;

  /**
   * Creates a new {@code ConstructorFingerprint} for the specified arrays of
   * parameters and exceptions.
   *
   * @param parameterTypes The array of class names in the parameter signature,
   *          or {@code null} if there are no parameters.
   * @param exceptionTypes The sorted array of class names in the exception
   *          signature, or {@code null} if there are no exceptions.
   */
  ConstructorFingerprint(final List<String> parameterTypes, final List<String> exceptionTypes) {
    this.parameterTypes = AssembleUtil.sort(parameterTypes);
    this.exceptionTypes = AssembleUtil.sort(exceptionTypes);
  }

  /**
   * @return The parameter type names.
   */
  List<String> getParameterTypes() {
    return this.parameterTypes;
  }

  /**
   * @return The exception type names.
   */
  List<String> getExceptionTypes() {
    return this.exceptionTypes;
  }

  @Override
  public int compareTo(final ConstructorFingerprint o) {
    return AssembleUtil.compare(parameterTypes, o.parameterTypes);
  }

  @Override
  public boolean equals(final Object obj) {
    if (obj == this)
      return true;

    if (!(obj instanceof ConstructorFingerprint))
      return false;

    final ConstructorFingerprint that = (ConstructorFingerprint)obj;
    if (parameterTypes == null ? that.parameterTypes != null : that.parameterTypes == null || !parameterTypes.equals(that.parameterTypes))
      return false;

    return true;
  }

  @Override
  public String toString() {
    final StringBuilder builder = new StringBuilder();
    builder.append("(");
    if (parameterTypes != null)
      builder.append(AssembleUtil.toString(parameterTypes, ","));

    builder.append(")");
    if (exceptionTypes != null)
      builder.append(" throws ").append(AssembleUtil.toString(exceptionTypes, ","));

    return builder.toString();
  }
}