package io.opentracing.contrib.specialagent.adaption;

import java.util.LinkedHashMap;
import java.util.Map;

import io.opentracing.Span;
import io.opentracing.contrib.specialagent.Function;

public class LogFieldAdapter extends Adapter {
  private final Adaptive source;
  private final Span target;

  private Map<String,Object> fields;

  LogFieldAdapter(final AdaptionRules rules, final Adaptive source, final Span target) {
    super(rules);
    this.source = source;
    this.target = target;
  }

  final void processLog(final long timestampMicroseconds, final Map<String,?> fields) {
    for (final Map.Entry<String,?> entry : fields.entrySet()) {
      final String key = entry.getKey();
      final Object value = entry.getValue();
      for (final AdaptionRule<?> rule : rules.getSpanRules(key)) {
        if (rule.type != AdaptionRuleType.LOG)
          continue;

        final Function<Object,Object> match = rule.match(value);
        if (match != null) {
          replaceLog(timestampMicroseconds, fields, rule, match);
          return;
        }
      }
    }

    // nothing matched
    log(fields, timestampMicroseconds);
  }

  private void replaceLog(final long timestampMicroseconds, final Map<String,?> fields, final AdaptionRule<?> matchedRule, final Function<Object,Object> match) {
    for (final Map.Entry<String,?> entry : fields.entrySet()) {
      final String key = entry.getKey();
      final Object value = entry.getValue();
      if (key.equals(matchedRule.key))
        processMatch(matchedRule, match);
      else if (!processRules(AdaptionRuleType.LOG, key, value))
        adaptLogField(key, value);
    }

    this.log(timestampMicroseconds);
  }

  void log(final Map<String,?> fields, final long timestampMicroseconds) {
    if (timestampMicroseconds > 0)
      target.log(timestampMicroseconds, fields);
    else
      target.log(fields);
  }

  void log(final long timestampMicroseconds) {
    if (fields != null)
      log(fields, timestampMicroseconds);
  }

  @Override
  void adaptLogField(final String key, final Object value) {
    if (fields == null)
      fields = new LinkedHashMap<>();

    fields.put(key, value);
  }

  @Override
  void adaptTag(final String key, final Object value) {
    source.adaptTag(key, value);
  }

  @Override
  void adaptOperationName(final String name) {
    source.adaptOperationName(name);
  }

  Map<String,Object> getFields() {
    return fields;
  }
}