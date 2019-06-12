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

import java.lang.reflect.Modifier;

/**
 * Enum representing the visibility access modifiers.
 */
enum Visibility {
  PRIVATE(Modifier.PRIVATE),
  PROTECTED(Modifier.PROTECTED),
  PACKAGE(Modifier.PUBLIC | Modifier.PROTECTED | Modifier.PRIVATE, 0),
  PUBLIC(Modifier.PUBLIC);

  private final int modifier;
  private final int test;

  Visibility(final int modifier, final int test) {
    this.modifier = modifier;
    this.test = test;
  }

  Visibility(final int modifier) {
    this(modifier, modifier);
  }

  /**
   * Returns the {@code Visibility} instance representing the visibility bit
   * set in the specified access modifier.
   *
   * @param modifier The access modifier for which to return the
   *          {@code Visibility}.
   * @return The {@code Visibility} instance representing the visibility bit
   *         set in the specified access modifier.
   */
  static Visibility get(final int modifier) {
    for (final Visibility visibility : values())
      if ((modifier & visibility.modifier) == visibility.test)
        return visibility;

    return null;
  }
}