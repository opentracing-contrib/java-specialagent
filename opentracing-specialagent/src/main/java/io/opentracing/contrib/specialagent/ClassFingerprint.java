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

import java.util.Arrays;

/**
 * A {@link NamedFingerprint} that represents the fingerprint of a
 * {@code Class}.
 */
class ClassFingerprint extends NamedFingerprint<ClassFingerprint> {
  private static final long serialVersionUID = 3179458505281585557L;

  private final String superClass;
  private final ConstructorFingerprint[] constructors;
  private final MethodFingerprint[] methods;
  private final FieldFingerprint[] fields;

  /**
   * Creates a new {@code ClassFingerprint} for the specified {@code Class}.
   *
   * @param cls The {@code Class} to be fingerprinted.
   */
  ClassFingerprint(final Class<?> cls) {
    super(cls.getName());
    this.superClass = cls.getSuperclass() == null || cls.getSuperclass() == Object.class ? null : cls.getSuperclass().getName();
    this.constructors = Util.sort(ConstructorFingerprint.recurse(cls.getDeclaredConstructors(), 0, 0));
    this.methods = Util.sort(MethodFingerprint.recurse(cls.getDeclaredMethods(), 0, 0));
    this.fields = Util.sort(FieldFingerprint.recurse(cls.getDeclaredFields(), 0, 0));
  }

  /**
   * Returns the name of the super {@code Class}, or {@code null} if the super
   * class is {@code Object.class}.
   *
   * @return The name of the super {@code Class}, or {@code null} if the super
   *         class is {@code Object.class}.
   */
  public String getSuperClass() {
    return this.superClass;
  }

  /**
   * Returns an array of {@link FieldFingerprint} objects, or {@code null} if no
   * fields are present in this fingerprint.
   *
   * @return An array of {@link FieldFingerprint} objects, or {@code null} if no
   *         fields are present in this fingerprint.
   */
  public FieldFingerprint[] getFields() {
    return this.fields;
  }

  /**
   * Returns an array of {@link ConstructorFingerprint} objects, or {@code null}
   * if no constructors are present in this fingerprint.
   *
   * @return An array of {@link ConstructorFingerprint} objects, or {@code null}
   *         if no constructors are present in this fingerprint.
   */
  public ConstructorFingerprint[] getConstructors() {
    return this.constructors;
  }

  /**
   * Returns an array of {@link MethodFingerprint} objects, or {@code null} if
   * no methods are present in this fingerprint.
   *
   * @return An array of {@link MethodFingerprint} objects, or {@code null} if
   *         no methods are present in this fingerprint.
   */
  public MethodFingerprint[] getMethods() {
    return this.methods;
  }

  /**
   * Tests if the specified {@code ClassFingerprint} is compatible with this
   * fingerprint. A fingerprint {@code a} is considered to be compatible with
   * fingerprint {@code b} if {@code a} contains all, or a subset of, field,
   * method, and constructor fingerprints in the fingerprint of {@code b}.
   *
   * @param o The fingerprint to check for compatibility with this fingerprint.
   * @return {@code true} if the specified {@code ClassFingerprint} is
   *         compatible with this fingerprint.
   */
  public boolean compatible(final ClassFingerprint o) {
    if (superClass != null ? !superClass.equals(o.superClass) : o.superClass != null)
      return false;

    if (constructors == null ? o.constructors != null : o.constructors == null || !Util.containsAll(constructors, o.constructors))
      return false;

    if (methods == null ? o.methods != null : o.methods == null || !Util.containsAll(methods, o.methods))
      return false;

    if (fields == null ? o.fields != null : o.fields == null || !Util.containsAll(fields, o.fields))
      return false;

    return true;
  }

  @Override
  public boolean equals(final Object obj) {
    if (obj == this)
      return true;

    if (!(obj instanceof ClassFingerprint))
      return false;

    final ClassFingerprint that = (ClassFingerprint)obj;
    if (superClass != null ? !superClass.equals(that.superClass) : that.superClass != null)
      return false;

    if (constructors == null ? that.constructors != null : that.constructors == null || !Arrays.equals(constructors, that.constructors))
      return false;

    if (methods == null ? that.methods != null : that.methods == null || !Arrays.equals(methods, that.methods))
      return false;

    if (fields == null ? that.fields != null : that.fields == null || !Arrays.equals(fields, that.fields))
      return false;

    return true;
  }

  @Override
  public String toString() {
    final StringBuilder builder = new StringBuilder();
    builder.append("class ").append(getName());
    if (superClass != null)
      builder.append(" extends ").append(superClass);

    builder.append(" {\n");
    if (constructors != null)
      builder.append("  ").append(getName()).append(Util.toString(constructors, "\n  " + getName())).append('\n');

    if (methods != null)
      builder.append("  ").append(Util.toString(methods, "\n  ")).append('\n');

    if (fields != null)
      builder.append("  ").append(Util.toString(fields, "\n  ")).append('\n');

    builder.append('}');
    return builder.toString();
  }
}