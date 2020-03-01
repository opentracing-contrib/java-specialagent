package io.opentracing.contrib.specialagent.adaption;

import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;

class AdaptionRule {
  static final String GLOBAL_RULES = "*";

  public static Map<String,AdaptionRules> parseRules(final InputStream inputStream) {
    try {
      Map<String,AdaptionRules> result = null;
      final JsonObject root = JsonParser.object().from(inputStream);
      final JsonArray globalRuleEntry = root.getArray(GLOBAL_RULES);
      final AdaptionRules globalRules = globalRuleEntry == null ? null : parseRules(globalRuleEntry, GLOBAL_RULES);

      for (final String key : root.keySet()) {
        final JsonArray jsonRules = root.getArray(key);
        if (jsonRules == null)
          throw new IllegalArgumentException(key + ": Is not an array");

        if (result == null)
          result = new LinkedHashMap<>();

        final AdaptionRules rules = parseRules(jsonRules, key);
        if (globalRules != null)
          rules.addAll(globalRules);

        result.put(key, rules);
      }

      return result != null ? result : Collections.EMPTY_MAP;
    }
    catch (final JsonParserException | PatternSyntaxException e) {
      throw new IllegalArgumentException(e);
    }
  }

  static AdaptionRules parseRules(final JsonArray jsonRules, final String key) {
    final AdaptionRules rules = new AdaptionRules();
    final int size = jsonRules.size();
    for (int i = 0; i < size; ++i)
      rules.add(parseRule(jsonRules.getObject(i), key + ".rules[" + i + "]"));

    return rules;
  }

  private static AdaptionRule parseRule(final JsonObject jsonRule, final String subject) {
    Objects.requireNonNull(jsonRule, subject + ": Not an object");
    final JsonArray jsonOutputs = jsonRule.getArray("outputs");
    Adaption[] outputs = null;
    if (jsonOutputs != null) {
      final int size = jsonOutputs.size();
      outputs = new Adaption[size];
      for (int i = 0; i < size; ++i) {
        final JsonObject jsonOutput = jsonOutputs.getObject(i);
        outputs[i] = new Adaption(jsonOutput, subject + ".outputs[" + i + "]");
      }
    }

    final AdaptionInput input = new AdaptionInput(jsonRule.getObject("input"), subject);
    final AdaptionRule rule = new AdaptionRule(input, outputs);
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

  final AdaptionInput input;
  final Adaption[] outputs;

  AdaptionRule(final AdaptionInput input, final Adaption[] outputs) {
    this.input = input;
    this.outputs = outputs;
  }

  Object match(final Object input) {
    if (this.input.getValue() == null)
      return SIMPLE;

    if (!(this.input.getValue() instanceof Pattern))
      return matchesSimpleValue(this.input.getValue(), input) ? SIMPLE : null;

    final Matcher matcher = ((Pattern)this.input.getValue()).matcher(input.toString());
    return matcher.matches() ? matcher : null;
  }

  Object adapt(final Object matcher, final Object input, final Object output) {
    return output == null ? input : matcher == SIMPLE ? output : ((Matcher)matcher).replaceAll(output.toString());
  }

  void validate(final String subject) {
    this.input.getType().validate(this, subject);
  }
}