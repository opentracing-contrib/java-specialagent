package io.opentracing.contrib.specialagent.tracer;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import io.opentracing.contrib.specialagent.Level;
import io.opentracing.contrib.specialagent.Logger;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class SpanRuleParser {
  private final Logger logger = Logger.getLogger(SpanRuleParser.class);

  public Map<String, SpanRules> parseRules(InputStream inputStream) {
    Map<String, SpanRules> result = new LinkedHashMap<>();

    try {
      try {
        JsonObject root = JsonParser.object().from(inputStream);

        for (String key : root.keySet()) {
          String subject = key + ": ";
          try {
            JsonArray ruleEntry = Objects.requireNonNull(root.getArray(key), subject + "not an array");
            result.put(key, new SpanRules(parseRules(ruleEntry, subject)));
          } catch (Exception e) {
            logger.log(Level.WARNING, "could not parse customizer rules for " + key, e);
          }
        }
      } finally {
        inputStream.close();
      }
    } catch (Exception e) {
      logger.log(Level.WARNING, "could not parse customizer rules", e);
    }
    return result;
  }

  List<SpanRule> parseRules(JsonArray jsonRules, String subject) {
    List<SpanRule> rules = new ArrayList<>();

    for (int i = 0; i < jsonRules.size(); i++) {
      String ruleSubject = subject + "rule " + i + ": ";
      JsonObject jsonRule = Objects.requireNonNull(jsonRules.getObject(i), ruleSubject + "not an object");
      rules.add(parseRule(jsonRule, ruleSubject));
    }

    return rules;
  }

  private SpanRule parseRule(JsonObject jsonRule, String subject) {
    SpanRuleType type = parseType(jsonRule.getString("type"), subject);
    String key = jsonRule.getString("key");

    List<SpanRuleOutput> outputs = new ArrayList<>();
    JsonArray jsonOutputs = jsonRule.getArray("output");
    if (jsonOutputs != null) {
      for (int i = 0; i < jsonOutputs.size(); i++) {
        outputs.add(parseOutput(jsonOutputs, i, subject + "output " + i + ": "));
      }
    }

    SpanRule rule = new SpanRule(parseMatcher(jsonRule), type, key, outputs);
    new SpanRuleValidator().validateRule(rule, subject);
    return rule;
  }

  private SpanRuleOutput parseOutput(JsonArray jsonOutputs, int i, String subject) {
    JsonObject jsonOutput = Objects.requireNonNull(jsonOutputs.getObject(i), subject + "not an object");
    return new SpanRuleOutput(
      parseType(jsonOutput.getString("type"), subject),
      jsonOutput.getString("key"),
      jsonOutput.get("value")
    );
  }

  private SpanRuleMatcher parseMatcher(JsonObject jsonRule) {
    String valueRegex = jsonRule.getString("valueRegex");
    if (valueRegex != null) {
      return new RegexSpanRuleMatcher(valueRegex);
    } else {
      return new PlainSpanRuleMatcher(jsonRule.get("value"));
    }
  }

  private SpanRuleType parseType(String type, String subject) {
    return SpanRuleType.valueOf(Objects.requireNonNull(type,  subject + "type is null"));
  }
}
