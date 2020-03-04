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

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;

final class RewriteRules implements Cloneable {
  public static List<RewriteRules> parseRules(final InputStream inputStream) {
    try {
      List<RewriteRules> result = null;
      final JsonObject root = JsonParser.object().from(inputStream);
      for (final String key : root.keySet()) {
        final JsonArray jsonRules = root.getArray(key);
        if (jsonRules == null)
          throw new IllegalArgumentException(key + ": Is not an array");

        if (result == null)
          result = new ArrayList<>();

        result.add(parseRules(jsonRules, key));
      }

      return result != null ? result : Collections.EMPTY_LIST;
    }
    catch (final JsonParserException | PatternSyntaxException e) {
      throw new IllegalArgumentException(e);
    }
  }

  static RewriteRules parseRules(final JsonArray jsonRules, final String key) {
    final RewriteRules rules = new RewriteRules(AssembleUtil.convertToNameRegex(key));
    final int size = jsonRules.size();
    for (int i = 0; i < size; ++i) {
      final RewriteRule[] rule = RewriteRule.parseRule(jsonRules.getObject(i), key + ".rules[" + i + "]");
      for (int j = 0; j < rule.length; ++j)
        rules.add(rule[j]);
    }

    return rules;
  }

  final HashMap<String,List<RewriteRule>> keyToRules = new HashMap<>();
  final Pattern namePattern;

  private RewriteRules(final Pattern namePattern) {
    this.namePattern = namePattern;
  }

  private RewriteRules(final HashMap<String,List<RewriteRule>> keyToRules) {
    this.keyToRules.putAll(keyToRules);
    this.namePattern = null;
  }

  void add(final RewriteRule rule) {
    List<RewriteRule> list = keyToRules.get(rule.input.getKey());
    if (list == null)
      keyToRules.put(rule.input.getKey(), list = new ArrayList<>());

    list.add(rule);
  }

  void addAll(final RewriteRules rules) {
    for (final List<RewriteRule> value : rules.keyToRules.values())
      for (final RewriteRule rule : value)
        add(rule);
  }

  List<RewriteRule> getRules(final String key) {
    final List<RewriteRule> inputs = keyToRules.get(key);
    return inputs != null ? inputs : Collections.<RewriteRule>emptyList();
  }

  @Override
  public RewriteRules clone() {
    return new RewriteRules(new HashMap<>(keyToRules));
  }
}