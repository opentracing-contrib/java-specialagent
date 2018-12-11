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

/**
 * A {@link Fingerprint} that represents the fingerprint of a {@code Field}.
 *
 * @author Seva Safris
 */
class FieldFingerprint extends NamedFingerprint<FieldFingerprint> {
  private static final long serialVersionUID = 3516568839736210165L;

  private final String type;

  /**
   * Creates a new {@code FieldFingerprint} for the specified name and type.
   *
   * @param name The name of the field.
   * @param type THe class name of the field's type.
   */
  FieldFingerprint(final String name, final String type) {
    super(name);
    this.type = type;
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