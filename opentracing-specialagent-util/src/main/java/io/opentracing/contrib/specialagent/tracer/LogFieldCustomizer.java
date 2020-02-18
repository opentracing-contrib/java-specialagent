package io.opentracing.contrib.specialagent.tracer;

import java.util.LinkedHashMap;
import java.util.Map;

import io.opentracing.Span;
import io.opentracing.contrib.specialagent.Function;

public class LogFieldCustomizer extends SpanCustomizer {
  private final SpanCustomizer customizer;
  private final Span target;

  private Map<String,Object> fields;

  public LogFieldCustomizer(final SpanRules rules, final SpanCustomizer source, final Span target) {
    super(rules);
    this.customizer = source;
    this.target = target;
  }

  final void processLog(final long timestampMicroseconds, final Map<String,?> fields) {
    for (final Map.Entry<String,?> entry : fields.entrySet()) {
      final String key = entry.getKey();
      final Object value = entry.getValue();
      for (final SpanRule rule : rules.getSpanRules(key)) {
        if (rule.type != SpanRuleType.LOG)
          continue;

        final Function<Object,Object> match = SpanRuleMatcher.match(rule.predicate, value);
        if (match != null) {
          replaceLog(timestampMicroseconds, fields, rule, match);
          return;
        }
      }
    }

    // nothing matched
    log(fields, timestampMicroseconds);
  }

  private void replaceLog(final long timestampMicroseconds, final Map<String,?> fields, final SpanRule matchedRule, final Function<Object,Object> match) {
    for (final Map.Entry<String,?> entry : fields.entrySet()) {
      final String key = entry.getKey();
      final Object value = entry.getValue();
      if (key.equals(matchedRule.key))
        processMatch(matchedRule, match);
      else if (!processRules(SpanRuleType.LOG, key, value))
        this.addLogField(key, value);
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
  void addLogField(final String key, final Object value) {
    if (fields == null)
      fields = new LinkedHashMap<>();

    fields.put(key, value);
  }

  @Override
  void setTag(final String key, final Object value) {
    customizer.setTag(key, value);
  }

  @Override
  void setOperationName(final String name) {
    customizer.setOperationName(name);
  }
}