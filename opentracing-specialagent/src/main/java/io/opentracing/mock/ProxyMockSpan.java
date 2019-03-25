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

package io.opentracing.mock;

import java.util.Map;

import io.opentracing.Span;
import io.opentracing.SpanContext;

public class ProxyMockSpan extends MockSpan {
  final Span realSpan;

  public ProxyMockSpan(final MockSpan mockSpan, final Span realSpan) {
    super(mockSpan.mockTracer, mockSpan.operationName(), mockSpan.startMicros, mockSpan.tags, mockSpan.references);
    this.realSpan = realSpan;
  }

  @Override
  public ProxyMockSpanContext context() {
    final MockContext mockSpanContext = super.context();
    final SpanContext realSpanContext = realSpan.context();
    return new ProxyMockSpanContext(mockSpanContext, realSpanContext);
  }

  @Override
  public ProxyMockSpan setTag(final String key, final String value) {
    super.setTag(key, value);
    realSpan.setTag(key, value);
    return this;
  }

  @Override
  public ProxyMockSpan setTag(final String key, final boolean value) {
    super.setTag(key, value);
    realSpan.setTag(key, value);
    return this;
  }

  @Override
  public ProxyMockSpan setTag(final String key, final Number value) {
    super.setTag(key, value);
    realSpan.setTag(key, value);
    return this;
  }

  @Override
  public ProxyMockSpan log(final Map<String,?> fields) {
    super.log(fields);
    realSpan.log(fields);
    return this;
  }

  @Override
  public ProxyMockSpan log(final long timestampMicroseconds, final Map<String,?> fields) {
    super.log(timestampMicroseconds, fields);
    realSpan.log(timestampMicroseconds, fields);
    return this;
  }

  @Override
  public ProxyMockSpan log(final String event) {
    super.log(event);
    realSpan.log(event);
    return this;
  }

  @Override
  public ProxyMockSpan log(final long timestampMicroseconds, final String event) {
    super.log(timestampMicroseconds, event);
    realSpan.log(timestampMicroseconds, event);
    return this;
  }

  @Override
  public ProxyMockSpan setBaggageItem(final String key, final String value) {
    super.setBaggageItem(key, value);
    realSpan.setBaggageItem(key, value);
    return this;
  }

  @Override
  public String getBaggageItem(final String key) {
    final String mockBaggageItem = super.getBaggageItem(key);
    final String realBaggageItem = realSpan.getBaggageItem(key);
    if (mockBaggageItem != null ? !mockBaggageItem.equals(realBaggageItem) : realBaggageItem != null)
      throw new IllegalStateException();

    return mockBaggageItem;
  }

  @Override
  public ProxyMockSpan setOperationName(final String operationName) {
    super.setOperationName(operationName);
    realSpan.setOperationName(operationName);
    return this;
  }

  @Override
  public void finish() {
    super.finish();
    realSpan.finish();
  }

  @Override
  public void finish(final long finishMicros) {
    super.finish(finishMicros);
    realSpan.finish(finishMicros);
  }
}