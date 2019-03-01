/* Copyright 2019 The OpenTracing Authors
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

import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer.SpanBuilder;
import io.opentracing.mock.MockTracer;

public class ProxyMockSpanBuilder extends MockTracer.SpanBuilder {
  final SpanBuilder mockSpanBuilder;
  final SpanBuilder realSpanBuilder;

  public ProxyMockSpanBuilder(final SpanBuilder mockSpanBuilder, final SpanBuilder realSpanBuilder) {
    if (mockSpanBuilder != null ? realSpanBuilder == null : realSpanBuilder != null)
      throw new IllegalStateException();

    this.mockSpanBuilder = mockSpanBuilder;
    this.realSpanBuilder = realSpanBuilder;
  }

  @Override
  public ProxyMockSpanBuilder asChildOf(final SpanContext parent) {
    mockSpanBuilder.asChildOf(parent);
    realSpanBuilder.asChildOf(parent);
    return this;
  }

  @Override
  public ProxyMockSpanBuilder asChildOf(final Span parent) {
    mockSpanBuilder.asChildOf(parent);
    realSpanBuilder.asChildOf(parent);
    return this;
  }

  @Override
  public ProxyMockSpanBuilder addReference(final String referenceType, final SpanContext referencedContext) {
    mockSpanBuilder.addReference(referenceType, referencedContext);
    realSpanBuilder.addReference(referenceType, referencedContext);
    return this;
  }

  @Override
  public ProxyMockSpanBuilder ignoreActiveSpan() {
    mockSpanBuilder.ignoreActiveSpan();
    realSpanBuilder.ignoreActiveSpan();
    return this;
  }

  @Override
  public ProxyMockSpanBuilder withTag(final String key, final String value) {
    mockSpanBuilder.withTag(key, value);
    realSpanBuilder.withTag(key, value);
    return this;
  }

  @Override
  public ProxyMockSpanBuilder withTag(final String key, final boolean value) {
    mockSpanBuilder.withTag(key, value);
    realSpanBuilder.withTag(key, value);
    return this;
  }

  @Override
  public ProxyMockSpanBuilder withTag(final String key, final Number value) {
    mockSpanBuilder.withTag(key, value);
    realSpanBuilder.withTag(key, value);
    return this;
  }

  @Override
  public ProxyMockSpanBuilder withStartTimestamp(final long microseconds) {
    mockSpanBuilder.withStartTimestamp(microseconds);
    realSpanBuilder.withStartTimestamp(microseconds);
    return this;
  }

  @Override
  public ProxyMockScope startActive(final boolean finishSpanOnClose) {
    final Scope mockScope = mockSpanBuilder.startActive(finishSpanOnClose);
    final Scope realScope = realSpanBuilder.startActive(finishSpanOnClose);
    return new ProxyMockScope(mockScope, realScope);
  }

  @Override
  @Deprecated
  public ProxyMockSpan startManual() {
    final Span mockSpan = mockSpanBuilder.startManual();
    final Span realSpan = realSpanBuilder.startManual();
    return new ProxyMockSpan(mockSpan, realSpan);
  }

  @Override
  public ProxyMockSpan start() {
    final Span mockSpan = mockSpanBuilder.start();
    final Span realSpan = realSpanBuilder.start();
    return new ProxyMockSpan(mockSpan, realSpan);
  }
}