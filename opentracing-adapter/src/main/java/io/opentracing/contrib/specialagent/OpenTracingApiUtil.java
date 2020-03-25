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

import java.util.HashMap;

import io.opentracing.Span;
import io.opentracing.tag.Tags;

public final class OpenTracingApiUtil {
  /**
   * Sets {@link Tags#ERROR} on the specified {@link Span span}, and logs the
   * provided {@link Throwable} if not null.
   *
   * @param span The {@link Span} to which {@link Tags#ERROR} is to be set.
   * @param t The {@link Throwable} is to be logged in the specified {@link Span span}, which can be null.
   * @throws NullPointerException If {@code span} is null.
   */
  public static void setErrorTag(final Span span, final Throwable t) {
    span.setTag(Tags.ERROR, Boolean.TRUE);
    if (t != null) {
      final HashMap<String,Object> errorLogs = new HashMap<>(2);
      errorLogs.put("event", Tags.ERROR.getKey());
      errorLogs.put("error.object", t);
      span.log(errorLogs);
    }
  }

  private OpenTracingApiUtil() {
  }
}