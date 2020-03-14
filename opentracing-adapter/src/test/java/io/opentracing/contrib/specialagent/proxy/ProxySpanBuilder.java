package io.opentracing.contrib.specialagent.proxy;

import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer.SpanBuilder;
import io.opentracing.tag.Tag;

public class ProxySpanBuilder implements SpanBuilder {
  final SpanBuilder spanBuilder;

  public ProxySpanBuilder(final SpanBuilder spanBuilder) {
    this.spanBuilder = spanBuilder;
  }

  @Override
  public SpanBuilder asChildOf(final SpanContext parent) {
    return null;
  }

  @Override
  public SpanBuilder asChildOf(final Span parent) {
    return null;
  }

  @Override
  public SpanBuilder addReference(final String referenceType, final SpanContext referencedContext) {
    return null;
  }

  @Override
  public SpanBuilder ignoreActiveSpan() {
    return null;
  }

  @Override
  public SpanBuilder withTag(final String key, final String value) {
    return null;
  }

  @Override
  public SpanBuilder withTag(final String key, final boolean value) {
    return null;
  }

  @Override
  public SpanBuilder withTag(final String key, final Number value) {
    return null;
  }

  @Override
  public SpanBuilder withStartTimestamp(final long microseconds) {
    return null;
  }

  @Override
  public Scope startActive(final boolean finishSpanOnClose) {
    return null;
  }

  @Override
  public Span startManual() {
    return null;
  }

  @Override
  public Span start() {
    return null;
  }

  @Override
  public <T> SpanBuilder withTag(Tag<T> tag, T value) {
    return null;
  }
}