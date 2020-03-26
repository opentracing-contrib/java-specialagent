/* Copyright 2020 The OpenTracing Authors
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

public final class HackMojo {
  static Object getField(final Class<? super FingerprintMojo> cls, final Object obj, final String fieldName) {
    try {
      final Field field = cls.getDeclaredField(fieldName);
      field.setAccessible(true);
      return field.get(obj);
    }
    catch (final IllegalAccessException | NoSuchFieldException e) {
      throw new IllegalStateException(e);
    }
  }

  static void setField(final Class<? super FingerprintMojo> cls, final Object obj, final String fieldName, final Object value) {
    try {
      final Field field = cls.getDeclaredField(fieldName);
      field.setAccessible(true);
      field.set(obj, value);
    }
    catch (final IllegalAccessException | NoSuchFieldException e) {
      throw new IllegalStateException(e);
    }
  }

  private HackMojo() {
  }
}