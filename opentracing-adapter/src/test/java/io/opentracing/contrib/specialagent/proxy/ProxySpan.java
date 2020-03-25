package io.opentracing.contrib.specialagent.proxy;

import java.util.Map;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.tag.Tag;

public class ProxySpan implements Span {
  final Span span;

  public ProxySpan(final Span span) {
    this.span = span;
  }

  @Override
  public SpanContext context() {
    return null;
  }

  @Override
  public Span setTag(final String key, final String value) {
    return null;
  }

  @Override
  public Span setTag(final String key, final boolean value) {
    return null;
  }

  @Override
  public Span setTag(final String key, final Number value) {
    return null;
  }

  @Override
  public Span log(final Map<String,?> fields) {
    return null;
  }

  @Override
  public Span log(final long timestampMicroseconds, final Map<String,?> fields) {
    return null;
  }

  @Override
  public Span log(final String event) {
    return null;
  }

  @Override
  public Span log(final long timestampMicroseconds, final String event) {
    return null;
  }

  @Override
  public Span setBaggageItem(final String key, final String value) {
    return null;
  }

  @Override
  public String getBaggageItem(final String key) {
    return null;
  }

  @Override
  public Span setOperationName(final String operationName) {
    return null;
  }

  @Override
  public void finish() {
  }

  @Override
  public void finish(final long finishMicros) {
  }

  @Override
  public <T> Span setTag(Tag<T> tag, T value) {
    return null;
  }
}