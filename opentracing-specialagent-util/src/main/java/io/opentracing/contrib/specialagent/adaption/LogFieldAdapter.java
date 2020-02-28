package io.opentracing.contrib.specialagent.adaption;

import io.opentracing.Span;

import java.util.LinkedHashMap;
import java.util.Map;

final class LogFieldAdapter extends Adapter {
  private final Adaptive source;
  private final Span target;

  private Map<String,Object> fields;

  LogFieldAdapter(final AdaptionRules rules, final Adaptive source, final Span target) {
    super(rules);
    this.source = source;
    this.target = target;
  }

  void processLog(final long timestampMicroseconds, final Map<String,?> fields) {
    for (final Map.Entry<String,?> entry : fields.entrySet()) {
      final String key = entry.getKey();
      final Object value = entry.getValue();
      for (final AdaptionRule<?,?> rule : rules.getSpanRules(key)) {
        if (rule.type != AdaptionType.LOG)
          continue;

        final Object match = rule.match(value);
        if (match != null) {
          replaceLog(timestampMicroseconds, fields, rule, match, value);
          return;
        }
      }
    }

    // nothing matched
    log(timestampMicroseconds, fields);
  }

  private <T,V>void replaceLog(final long timestampMicroseconds, final Map<String,?> fields, final AdaptionRule<T,V> rule, final Object match, final Object input) {
    for (final Map.Entry<String,?> entry : fields.entrySet()) {
      final String key = entry.getKey();
      final Object value = entry.getValue();
      if (key.equals(rule.key))
        processMatch(rule, timestampMicroseconds, match, input);
      else if (!processRules(AdaptionType.LOG, timestampMicroseconds, key, value))
        adaptLog(timestampMicroseconds, key, value);
    }

    this.log(timestampMicroseconds);
  }

  void log(final long timestampMicroseconds, final Map<String,?> fields) {
    if (timestampMicroseconds > 0)
      target.log(timestampMicroseconds, fields);
    else
      target.log(fields);
  }

  void log(final long timestampMicroseconds) {
    if (fields != null)
      log(timestampMicroseconds, fields);
  }

  @Override
  void adaptLog(final long timestampMicroseconds, final String key, final Object value) {
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
