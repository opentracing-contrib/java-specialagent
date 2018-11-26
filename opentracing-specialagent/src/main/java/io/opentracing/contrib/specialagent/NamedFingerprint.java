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
 * Abstract class representing a {@link Fingerprint} with a name.
 *
 * @param <T> The self-type parameter.
 */
abstract class NamedFingerprint<T extends NamedFingerprint<T>> extends Fingerprint implements Comparable<T> {
  private static final long serialVersionUID = 3682401024183679159L;

  private final String name;

  /**
   * Creates a new {@code NamedFingerprint} with the specified name.
   *
   * @param name The name associated to this fingerprint.
   */
  NamedFingerprint(final String name) {
    this.name = name;
  }

  public final String getName() {
    return name;
  }

  @Override
  public int compareTo(final T o) {
    return getName().compareTo(o.getName());
  }
}