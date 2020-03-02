package io.opentracing.contrib.specialagent.rewrite;

import java.util.Collections;
import java.util.Map;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.tag.Tag;

public class RewritableSpan extends Rewriter implements Span {
  final Span target;
  private final RewriteRules rules;

  RewritableSpan(final Span target, final RewriteRules rules) {
    super(rules);
    this.target = target;
    this.rules = rules;
  }

  @Override
  public SpanContext context() {
    return target.context();
  }

  @Override
  public Span setTag(final String key, final String value) {
    onTag(key, value);
    return this;
  }

  @Override
  public Span setTag(final String key, final boolean value) {
    onTag(key, value);
    return this;
  }

  @Override
  public Span setTag(final String key, final Number value) {
    onTag(key, value);
    return this;
  }

  @Override
  public <T>Span setTag(final Tag<T> tag, final T value) {
    onTag(tag.getKey(), value);
    return this;
  }

  @Override
  public Span log(final Map<String,?> fields) {
    return log(0, fields);
  }

  @Override
  public Span log(final long timestampMicroseconds, final Map<String,?> fields) {
    newLogFieldRewriter().processLog(timestampMicroseconds, fields);
    return this;
  }

  LogFieldRewriter newLogFieldRewriter() {
    return new LogFieldRewriter(rules, this, target);
  }

  @Override
  public Span log(final String event) {
    return log(0, event);
  }

  private LogEventRewriter logEventRewriter;

  @Override
  public Span log(final long timestampMicroseconds, final String event) {
    if (logEventRewriter == null)
      logEventRewriter = new LogEventRewriter(rules, this, target);

    logEventRewriter.onLog(timestampMicroseconds, null, event);
    return this;
  }

  @Override
  public Span setBaggageItem(final String key, final String value) {
    target.setBaggageItem(key, value);
    return this;
  }

  @Override
  public String getBaggageItem(final String key) {
    return target.getBaggageItem(key);
  }

  @Override
  public Span setOperationName(final String operationName) {
    onOperationName(operationName);
    return this;
  }

  @Override
  public void finish() {
    target.finish();
  }

  @Override
  public void finish(final long finishMicros) {
    target.finish(finishMicros);
  }

  @Override
  void rewriteLog(final long timestampMicroseconds, final String key, final Object value) {
    if (timestampMicroseconds > 0)
      target.log(timestampMicroseconds, Collections.singletonMap(key, value));
    else
      target.log(Collections.singletonMap(key, value));
  }

  @Override
  void rewriteTag(final String key, final Object value) {
    if (value == null)
      target.setTag(key, (String)null);
    else if (value instanceof Number)
      target.setTag(key, (Number)value);
    else if (value instanceof Boolean)
      target.setTag(key, (Boolean)value);
    else
      target.setTag(key, value.toString());
  }

  @Override
  void rewriteOperationName(final String name) {
    target.setOperationName(name);
  }
}