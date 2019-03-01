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

import java.util.Map;

import io.opentracing.Span;
import io.opentracing.SpanContext;

public class ProxyMockSpan implements Span {
  final Span mockSpan;
  final Span realSpan;

  public ProxyMockSpan(final Span mockSpan, final Span realSpan) {
    if (mockSpan != null ? realSpan == null : realSpan != null)
      throw new IllegalStateException();

    this.mockSpan = mockSpan;
    this.realSpan = realSpan;
  }

  @Override
  public ProxyMockSpanContext context() {
    final SpanContext mockSpanContext = mockSpan.context();
    final SpanContext realSpanContext = realSpan.context();
    return new ProxyMockSpanContext(mockSpanContext, realSpanContext);
  }

  @Override
  public ProxyMockSpan setTag(final String key, final String value) {
    mockSpan.setTag(key, value);
    realSpan.setTag(key, value);
    return this;
  }

  @Override
  public ProxyMockSpan setTag(final String key, final boolean value) {
    mockSpan.setTag(key, value);
    realSpan.setTag(key, value);
    return this;
  }

  @Override
  public ProxyMockSpan setTag(final String key, final Number value) {
    mockSpan.setTag(key, value);
    realSpan.setTag(key, value);
    return this;
  }

  @Override
  public ProxyMockSpan log(final Map<String,?> fields) {
    mockSpan.log(fields);
    realSpan.log(fields);
    return this;
  }

  @Override
  public ProxyMockSpan log(final long timestampMicroseconds, final Map<String,?> fields) {
    mockSpan.log(timestampMicroseconds, fields);
    realSpan.log(timestampMicroseconds, fields);
    return this;
  }

  @Override
  public ProxyMockSpan log(final String event) {
    mockSpan.log(event);
    realSpan.log(event);
    return this;
  }

  @Override
  public ProxyMockSpan log(final long timestampMicroseconds, final String event) {
    mockSpan.log(timestampMicroseconds, event);
    realSpan.log(timestampMicroseconds, event);
    return this;
  }

  @Override
  public ProxyMockSpan setBaggageItem(final String key, final String value) {
    mockSpan.setBaggageItem(key, value);
    realSpan.setBaggageItem(key, value);
    return this;
  }

  @Override
  public String getBaggageItem(final String key) {
    final String mockBaggageItem = mockSpan.getBaggageItem(key);
    final String realBaggageItem = realSpan.getBaggageItem(key);
    if (mockBaggageItem != null ? !mockBaggageItem.equals(realBaggageItem) : realBaggageItem != null)
      throw new IllegalStateException();

    return mockBaggageItem;
  }

  @Override
  public ProxyMockSpan setOperationName(final String operationName) {
    mockSpan.setOperationName(operationName);
    realSpan.setOperationName(operationName);
    return this;
  }

  @Override
  public void finish() {
    mockSpan.finish();
    realSpan.finish();
  }

  @Override
  public void finish(final long finishMicros) {
    mockSpan.finish(finishMicros);
    realSpan.finish(finishMicros);
  }
}