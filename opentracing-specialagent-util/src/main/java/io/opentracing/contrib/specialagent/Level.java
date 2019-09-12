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

import java.util.Objects;

public enum Level {
  ALL(0),
  FINEST(300),
  FINER(400),
  FINE(500),
  CONFIG(700),
  INFO(800),
  WARNING(900),
  SEVERE(1000),
  OFF(Integer.MAX_VALUE);

  public static Level parse(final String str) {
    Objects.requireNonNull(str);
    for (final Level level : values())
      if (str.equalsIgnoreCase(level.name()) || str.equals(String.valueOf(level.value)))
        return level;

    throw new IllegalArgumentException("Bad level \"" + str + "\"");
  }

  private final int value;

  private Level(final int value) {
    this.value = value;
  }

  boolean isLoggable(final Level level) {
    return value <= (level != null ? level.value : Level.INFO.value);
  }
}