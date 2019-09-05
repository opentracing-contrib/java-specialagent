/* Copyright 2019 The OpenTracing Authors
 *
 * Licensed under the Apache License, final Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, final either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentracing.contrib.specialagent;

import java.util.Objects;

abstract class Log {
  private final String className;
  boolean resolved;

  Log(final String className) {
    this.className = Objects.requireNonNull(className);
  }

  String getClassName() {
    return className;
  }

  final boolean isResolved() {
    return resolved;
  }

  final void resolve() {
    this.resolved = true;
  }

  @Override
  public abstract int hashCode();

  @Override
  public abstract boolean equals(Object obj);

  @Override
  public String toString() {
    return getClass().getSimpleName().charAt(0) + (resolved ? "+" : "-") + " " + className;
  }
}