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

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * A {@link Fingerprint} that represents the fingerprint of a
 * {@code Field}.
 */
class FieldFingerprint extends NamedFingerprint<FieldFingerprint> {
  private static final long serialVersionUID = 3516568839736210165L;

  /**
   * Returns an array of {@code FieldFingerprint} objects for the
   * non-private and non-synthetic fields in the specified array of
   * {@code Field} objects. This is a recursive algorithm, and the
   * {@code index} and {@code depth} parameters are used to track the execution
   * state on the call stack.
   *
   * @param fields The {@code Field} objects to be fingerprinted.
   * @param index The index of the iteration (should be 0 when called).
   * @param depth The depth of the iteration (should be 0 when called).
   * @return An array of {@code FieldFingerprint} objects for the
   *         non-private and non-synthetic fields in the specified array
   *         of {@code Field} objects.
   */
  static FieldFingerprint[] recurse(final Field[] fields, final int index, final int depth) {
    for (int i = index; i < fields.length; ++i) {
      if (!Modifier.isPrivate(fields[i].getModifiers())) {
        final FieldFingerprint fingerprint = new FieldFingerprint(fields[i]);
        final FieldFingerprint[] fingerprints = recurse(fields, i + 1, depth + 1);
        fingerprints[depth] = fingerprint;
        return fingerprints;
      }
    }

    return depth == 0 ? null : new FieldFingerprint[depth];
  }

  private final String type;

  /**
   * Creates a new {@code FieldFingerprint} for the specified
   * {@code Field}.
   *
   * @param field The {@code Field} to be fingerprinted.
   */
  FieldFingerprint(final Field field) {
    super(field.getName());
    this.type = Util.getName(field.getType());
  }

  @Override
  public boolean equals(final Object obj) {
    if (obj == this)
      return true;

    if (!(obj instanceof FieldFingerprint))
      return false;

    final FieldFingerprint that = (FieldFingerprint)obj;
    return getName().equals(that.getName()) && type.equals(that.type);
  }

  @Override
  public String toString() {
    return type + " " + getName();
  }
}