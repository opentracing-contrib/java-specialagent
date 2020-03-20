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

import io.opentracing.Span;

final class LogEventRewriter extends Rewriter {
  private final Rewriter source;
  private final Span target;

  LogEventRewriter(final RewriteRules rules, final Rewriter source, final Span target) {
    super(rules);
    this.source = source;
    this.target = target;
  }

  @Override
  void rewriteLog(final long timestampMicroseconds, final String key, final Object value) {
    if (timestampMicroseconds > 0)
      target.log(timestampMicroseconds, value.toString());
    else
      target.log(value.toString());
  }

  @Override
  void rewriteTag(final String key, final Object value) {
    source.rewriteTag(key, value);
  }

  @Override
  void rewriteOperationName(final String name) {
    source.rewriteOperationName(name);
  }
}