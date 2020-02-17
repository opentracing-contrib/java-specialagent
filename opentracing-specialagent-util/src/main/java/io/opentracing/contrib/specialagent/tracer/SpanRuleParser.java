package io.opentracing.contrib.specialagent.tracer;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;

public final class SpanRuleParser {
  public static Map<String,SpanRules> parseRules(final InputStream inputStream) {
    try {
      final Map<String,SpanRules> result = new LinkedHashMap<>();
      final JsonObject root = JsonParser.object().from(inputStream);
      for (final String key : root.keySet()) {
        final String subject = key + ": ";
        final JsonArray ruleEntry = root.getArray(key);
        if (ruleEntry == null)
          throw new IllegalArgumentException("\"" + key + "\" is not an array");

        result.put(key, new SpanRules(parseRules(ruleEntry, subject)));
      }

      return result;
    }
    catch (final JsonParserException e) {
      throw new IllegalArgumentException(e);
    }
  }

  static List<SpanRule> parseRules(final JsonArray jsonRules, final String subject) {
    final List<SpanRule> rules = new ArrayList<>();
    for (int i = 0, size = jsonRules.size(); i < size; ++i) {
      final String ruleSubject = subject + "rule " + i + ": ";
      final JsonObject jsonRule = Objects.requireNonNull(jsonRules.getObject(i), ruleSubject + "not an object");
      rules.add(parseRule(jsonRule, ruleSubject));
    }

    return rules;
  }

  private static SpanRule parseRule(final JsonObject jsonRule, final String subject) {
    final SpanRuleType type = parseType(jsonRule.getString("type"), subject);
    final String key = jsonRule.getString("key");

    final List<SpanRuleOutput> outputs = new ArrayList<>();
    final JsonArray jsonOutputs = jsonRule.getArray("output");
    if (jsonOutputs != null)
      for (int i = 0, size = jsonOutputs.size(); i < size; ++i)
        outputs.add(parseOutput(jsonOutputs, i, subject + "output " + i + ": "));

    final SpanRule rule = new SpanRule(parseMatcher(jsonRule), type, key, outputs);
    rule.type.validate(rule, subject);
    return rule;
  }

  private static SpanRuleOutput parseOutput(final JsonArray jsonOutputs, final int i, final String subject) {
    final JsonObject jsonOutput = Objects.requireNonNull(jsonOutputs.getObject(i), subject + "not an object");
    return new SpanRuleOutput(parseType(jsonOutput.getString("type"), subject), jsonOutput.getString("key"), jsonOutput.get("value"));
  }

  private static Object parseMatcher(final JsonObject jsonRule) {
    final String valueRegex = jsonRule.getString("valueRegex");
    return valueRegex != null ? Pattern.compile(valueRegex) : jsonRule.get("value");
  }

  private static SpanRuleType parseType(final String type, final String subject) {
    return SpanRuleType.fromString(Objects.requireNonNull(type, subject + "type is null"));
  }

  private SpanRuleParser() {
  }
}