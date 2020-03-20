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

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;

class RewriteRule {
  static RewriteRule[] parseRule(final JsonObject jsonRule, final String subject) {
    Objects.requireNonNull(jsonRule, subject + ": Not an object");
    Action[] outputs = null;

    final JsonArray jsonOutputs = jsonRule.getArray("output");
    if (jsonOutputs != null) {
      final int size = jsonOutputs.size();
      outputs = new Action[size];
      for (int i = 0; i < size; ++i)
        outputs[i] = Action.parseOutputEvent(jsonOutputs.getObject(i), subject + ".output[" + i + "]");
    }
    else {
      final JsonObject jsonOutput = jsonRule.getObject("output");
      if (jsonOutput != null)
        outputs = new Action[] {Action.parseOutputEvent(jsonOutput, subject + ".output")};
    }

    final RewriteRule[] rules;
    final JsonArray jsonInputs = jsonRule.getArray("input");
    if (jsonInputs != null) {
      final int len = jsonInputs.size();
      rules = new RewriteRule[len];
      for (int i = 0; i < len; ++i)
        rules[i] = parseInput(jsonInputs.getObject(i), outputs, subject + ".input[" + i + "]").validate(subject);
    }
    else {
      final JsonObject jsonInput = jsonRule.getObject("input");
      rules = new RewriteRule[] {parseInput(jsonInput, outputs, subject + ".input").validate(subject)};
    }

    return rules;
  }

  private static RewriteRule parseInput(final JsonObject jsonInput, final Action[] outputs, final String subject) {
    final Action input = Action.parseInputEvent(jsonInput, subject);
    final RewriteRule rule = new RewriteRule(input, outputs);
    return rule;
  }

  private static boolean matchesSimpleValue(final Object predicate, final Object value) {
    if (predicate instanceof Number && value instanceof Number)
      return ((Number)predicate).doubleValue() == ((Number)value).doubleValue();

    return predicate.equals(value);
  }

  static final Boolean SIMPLE = Boolean.TRUE;

  final Action input;
  final Action[] outputs;

  RewriteRule(final Action input, final Action[] outputs) {
    this.input = input;
    this.outputs = outputs;
  }

  final void rewrite(final Rewriter rewriter, final long timestampMicroseconds, final Object match, final Object input) {
    if (outputs != null) {
      for (final Action output : outputs) {
        final String outputKey = output.getKey() != null ? output.getKey() : this.input.getKey();
        final Object outputValue = rewriteValue(match, input, output.getValue());
        output.rewrite(rewriter, timestampMicroseconds, outputKey, outputValue);
      }
    }
  }

  final Object matchValue(final Object input) {
    if (this.input.getValue() == null)
      return SIMPLE;

    if (!(this.input.getValue() instanceof Pattern))
      return matchesSimpleValue(this.input.getValue(), input) ? SIMPLE : null;

    final Matcher matcher = ((Pattern)this.input.getValue()).matcher(input.toString());
    return matcher.matches() ? matcher : null;
  }

  Object rewriteValue(final Object matcher, final Object input, final Object output) {
    return output == null ? input : matcher == SIMPLE ? output : ((Matcher)matcher).replaceAll(output.toString());
  }

  final RewriteRule validate(final String subject) {
    this.input.validate(this, subject);
    return this;
  }
}