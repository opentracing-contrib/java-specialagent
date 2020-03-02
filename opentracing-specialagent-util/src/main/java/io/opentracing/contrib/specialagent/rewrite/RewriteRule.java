package io.opentracing.contrib.specialagent.rewrite;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;

class RewriteRule {
  static RewriteRule parseRule(final JsonObject jsonRule, final String subject) {
    Objects.requireNonNull(jsonRule, subject + ": Not an object");
    final JsonArray jsonOutputs = jsonRule.getArray("outputs");
    Event[] outputs = null;
    if (jsonOutputs != null) {
      final int size = jsonOutputs.size();
      outputs = new Event[size];
      for (int i = 0; i < size; ++i)
        outputs[i] = Event.parseOutputEvent(jsonOutputs.getObject(i), subject + ".outputs[" + i + "]");
    }

    final Event input = Event.parseInputEvent(jsonRule.getObject("input"), subject + ".input");
    final RewriteRule rule = new RewriteRule(input, outputs);
    rule.validate(subject);
    return rule;
  }

  private static boolean matchesSimpleValue(final Object predicate, final Object value) {
    if (predicate == null)
      return true;

    if (predicate instanceof Number && value instanceof Number)
      return ((Number)predicate).doubleValue() == ((Number)value).doubleValue();

    return predicate.equals(value);
  }

  static final Boolean SIMPLE = Boolean.TRUE;

  final Event input;
  final Event[] outputs;

  RewriteRule(final Event input, final Event[] outputs) {
    this.input = input;
    this.outputs = outputs;
  }

  final void rewrite(final Rewriter rewriter, final long timestampMicroseconds, final Object match, final Object input) {
    if (outputs != null) {
      for (final Event output : outputs) {
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

  final void validate(final String subject) {
    this.input.validate(this, subject);
  }
}