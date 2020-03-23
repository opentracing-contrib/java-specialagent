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

abstract class Rewriter {
  final RewriteRules rules;

  Rewriter(final RewriteRules rules) {
    this.rules = rules;
  }

  abstract void rewriteTag(String key, Object value);
  abstract void rewriteLog(long timestampMicroseconds, String key, Object value);
  abstract void rewriteOperationName(String name);

  final void onOperationName(final String operationName) {
    if (!onEvent(Action.OperationName.class, 0, null, operationName))
      rewriteOperationName(operationName);
  }

  final void onLog(final long timestampMicroseconds, final String key, final Object value) {
    if (!onEvent(Action.Log.class, timestampMicroseconds, key, value))
      rewriteLog(timestampMicroseconds, key, value);
  }

  final void onTag(final String key, final Object value) {
    if (!onEvent(Action.Tag.class, 0, key, value))
      rewriteTag(key, value);
  }

  private boolean onEvent(final Class<? extends Action> type, final long timestampMicroseconds, final String key, final Object value) {
    if (rules == null)
      return false;

    for (final RewriteRule rule : rules.getRules(key)) {
      if (rule.input.getClass() != type)
        continue;

      final Object match = rule.matchValue(value);
      if (match != null) {
        rule.rewrite(this, timestampMicroseconds, match, value);
        return true;
      }
    }

    return false;
  }
}