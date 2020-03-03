package io.opentracing.contrib.specialagent;

import java.util.LinkedHashMap;
import java.util.Map;

import io.opentracing.Span;

class LogFieldRewriter extends Rewriter {
  private final Rewriter source;
  private final Span target;

  private Map<String,Object> fields;

  LogFieldRewriter(final RewriteRules rules, final Rewriter source, final Span target) {
    super(rules);
    this.source = source;
    this.target = target;
  }

  void processLog(final long timestampMicroseconds, final Map<String,?> fields) {
    for (final Map.Entry<String,?> entry : fields.entrySet()) {
      final String key = entry.getKey();
      final Object value = entry.getValue();
      for (final RewriteRule rule : rules.getRules(key)) {
        if (rule.input.getClass() != Action.Log.class)
          continue;

        final Object match = rule.matchValue(value);
        if (match != null) {
          rewriteLog(timestampMicroseconds, fields, rule, match, value);
          return;
        }
      }
    }

    // nothing matched
    log(timestampMicroseconds, fields);
  }

  private void rewriteLog(final long timestampMicroseconds, final Map<String,?> fields, final RewriteRule rule, final Object match, final Object input) {
    for (final Map.Entry<String,?> entry : fields.entrySet()) {
      final String key = entry.getKey();
      final Object value = entry.getValue();
      if (key.equals(rule.input.getKey()))
        rule.rewrite(this, timestampMicroseconds, match, input);
      else
        onLog(timestampMicroseconds, key, value);
    }

    if (this.fields != null)
      log(timestampMicroseconds, this.fields);
  }

  private void log(final long timestampMicroseconds, final Map<String,?> fields) {
    if (timestampMicroseconds > 0)
      target.log(timestampMicroseconds, fields);
    else
      target.log(fields);
  }

  @Override
  void rewriteLog(final long timestampMicroseconds, final String key, final Object value) {
    if (fields == null)
      fields = new LinkedHashMap<>();

    fields.put(key, value);
  }

  @Override
  void rewriteTag(final String key, final Object value) {
    source.rewriteTag(key, value);
  }

  @Override
  void rewriteOperationName(final String name) {
    source.rewriteOperationName(name);
  }
}