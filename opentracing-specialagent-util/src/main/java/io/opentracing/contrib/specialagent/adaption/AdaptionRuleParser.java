package io.opentracing.contrib.specialagent.adaption;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;

import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

public final class AdaptionRuleParser {
  public static final String GLOBAL_RULES = "*";

  public static Map<String,AdaptionRules> parseRules(final InputStream inputStream) {
    try {
      Map<String,AdaptionRules> result = null;
      final JsonObject root = JsonParser.object().from(inputStream);
      final JsonArray globalRuleEntry = root.getArray(GLOBAL_RULES);
      final AdaptionRules globalRules = globalRuleEntry == null ? null : parseRules(globalRuleEntry, GLOBAL_RULES);

      for (final String key : root.keySet()) {
        final JsonArray ruleEntry = root.getArray(key);
        if (ruleEntry == null)
          throw new IllegalArgumentException("\"" + key + "\" is not an array");

        if (result == null)
          result = new LinkedHashMap<>();

        final AdaptionRules rules = parseRules(ruleEntry, key);
        if (globalRules != null)
          rules.addAll(globalRules);

        result.put(key, rules);
      }

      return result != null ? result : Collections.EMPTY_MAP;
    }
    catch (final JsonParserException e) {
      throw new IllegalArgumentException(e);
    }
  }

  static AdaptionRules parseRules(final JsonArray jsonRules, final String key) {
    final AdaptionRules rules = new AdaptionRules();
    final int size = jsonRules.size();
    for (int i = 0; i < size; ++i) {
      final String ruleSubject = key + ": rule " + i + ": ";
      final JsonObject jsonRule = Objects.requireNonNull(jsonRules.getObject(i), ruleSubject + "not an object");
      final AdaptionRule<?> rule = parseRule(jsonRule, ruleSubject);
      rules.add(rule);
    }

    return rules;
  }

  private static AdaptionRule<?> parseRule(final JsonObject jsonRule, final String subject) {
    final AdaptionRuleType type = parseType(jsonRule.getString("type"), subject);
    final String key = jsonRule.getString("key");
    final JsonArray jsonOutputs = jsonRule.getArray("output");
    AdaptedOutput[] outputs = null;
    if (jsonOutputs != null) {
      final int size = jsonOutputs.size();
      outputs = new AdaptedOutput[size];
      for (int i = 0; i < size; ++i)
        outputs[i] = parseOutput(jsonOutputs, i, subject + "output " + i + ": ");
    }

    final AdaptionRule<?> rule;
    final String valueRegex = jsonRule.getString("valueRegex");
    if (valueRegex != null)
      rule = new PatternAdaptionRule(Pattern.compile(valueRegex), type, key, outputs);
    else
      rule = new SimpleAdaptionRule(jsonRule.get("value"), type, key, outputs);

    rule.validate(subject);
    return rule;
  }

  private static AdaptedOutput parseOutput(final JsonArray jsonOutputs, final int i, final String subject) {
    final JsonObject jsonOutput = Objects.requireNonNull(jsonOutputs.getObject(i), subject + "not an object");
    return new AdaptedOutput(parseType(jsonOutput.getString("type"), subject), jsonOutput.getString("key"), jsonOutput.get("value"));
  }

  private static AdaptionRuleType parseType(final String type, final String subject) {
    return Objects.requireNonNull(AdaptionRuleType.fromString(Objects.requireNonNull(type, subject + "type is not a string")), subject + "invalid type");
  }

  private AdaptionRuleParser() {
  }
}