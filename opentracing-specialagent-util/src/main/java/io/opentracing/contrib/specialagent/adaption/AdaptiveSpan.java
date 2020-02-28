package io.opentracing.contrib.specialagent.adaption;

import java.util.Collections;
import java.util.Map;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.tag.Tag;

public class AdaptiveSpan extends Adaptive implements Span {
  private final Span target;
  private final AdaptionRules rules;

  AdaptiveSpan(final Span target, final AdaptionRules rules) {
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
    processTag(key, value);
    return this;
  }

  @Override
  public Span setTag(final String key, final boolean value) {
    processTag(key, value);
    return this;
  }

  @Override
  public Span setTag(final String key, final Number value) {
    processTag(key, value);
    return this;
  }

  @Override
  public <T>Span setTag(final Tag<T> tag, final T value) {
    processTag(tag.getKey(), value);
    return this;
  }

  @Override
  public Span log(final Map<String,?> fields) {
    return log(0, fields);
  }

  @Override
  public Span log(final long timestampMicroseconds, final Map<String,?> fields) {
    newLogFieldAdapter().processLog(timestampMicroseconds, fields);
    return this;
  }

  LogFieldAdapter newLogFieldAdapter() {
    return new LogFieldAdapter(rules, this, target);
  }

  @Override
  public Span log(final String event) {
    return log(0, event);
  }

  private LogEventAdapter logEventAdapter;

  @Override
  public Span log(final long timestampMicroseconds, final String event) {
    if (logEventAdapter == null)
      logEventAdapter = new LogEventAdapter(rules, this, target);

    logEventAdapter.processLog(timestampMicroseconds, event);
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
    processOperationName(operationName);
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
  void adaptLog(final long timestampMicroseconds, final String key, final Object value) {
    if (timestampMicroseconds > 0)
      target.log(timestampMicroseconds, Collections.singletonMap(key, value));
    else
      target.log(Collections.singletonMap(key, value));
  }

  @Override
  void adaptTag(final String key, final Object value) {
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
  void adaptOperationName(final String name) {
    target.setOperationName(name);
  }
}