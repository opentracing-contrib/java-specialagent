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
import java.util.Objects;

import io.opentracing.Scope;
import io.opentracing.ScopeManager;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.mock.ProxyMockScopeManager.ProxyMockScope.ProxyMockSpan;

class ProxyMockScopeManager implements ScopeManager {
  final ProxyMockTracer tracer;
  final ScopeManager mockScopeManager;
  final ScopeManager realScopeManager;

  ProxyMockScopeManager(final ProxyMockTracer tracer, final ScopeManager mockScopeManager, final ScopeManager realScopeManager) {
    this.tracer = Objects.requireNonNull(tracer);
    this.mockScopeManager = Objects.requireNonNull(mockScopeManager);
    this.realScopeManager = Objects.requireNonNull(realScopeManager);
  }

  @Override
  public Scope activate(final Span span) {
    if (!(span instanceof ProxyMockSpan))
      throw new IllegalStateException();

    final ProxyMockSpan proxyMockSpan = (ProxyMockSpan)span;
    final Scope mockScope = mockScopeManager.activate(proxyMockSpan.mockSpan);
    final Scope realScope = realScopeManager.activate(proxyMockSpan.realSpan);
    return mockScope == null ? null : new ProxyMockScope(tracer, mockScope, realScope);
  }

  @Override
  @Deprecated
  public ProxyMockScope active() {
    final Scope mockScope = mockScopeManager.active();
    final Scope realScope = realScopeManager.active();
    return mockScope == null ? null : new ProxyMockScope(tracer, mockScope, realScope);
  }

  @Override
  public ProxyMockSpan activeSpan() {
    final Span mockSpan = mockScopeManager.activeSpan();
    final Span realSpan = realScopeManager.activeSpan();
    return mockSpan == null ? null : new ProxyMockSpan(tracer, (MockSpan)mockSpan, realSpan);
  }

  @Override
  @Deprecated
  public ProxyMockScope activate(final Span span, final boolean finishSpanOnClose) {
    if (!(span instanceof ProxyMockSpan))
      throw new IllegalStateException();

    final ProxyMockSpan proxyMockSpan = (ProxyMockSpan)span;
    final Scope mockScope = mockScopeManager.activate(proxyMockSpan.mockSpan, finishSpanOnClose);
    final Scope realScope = realScopeManager.activate(proxyMockSpan.realSpan, finishSpanOnClose);
    return mockScope == null ? null : new ProxyMockScope(tracer, mockScope, realScope);
  }

  static class ProxyMockScope implements Scope {
    final ProxyMockTracer tracer;
    final Scope mockScope;
    final Scope realScope;
    private ProxyMockSpan span;

    ProxyMockScope(final ProxyMockTracer tracer, final Scope mockScope, final Scope realScope) {
      this.tracer = Objects.requireNonNull(tracer);
      this.mockScope = Objects.requireNonNull(mockScope);
      this.realScope = Objects.requireNonNull(realScope);
    }

    @Override
    public void close() {
      mockScope.close();
      realScope.close();
    }

    @Override
    @Deprecated
    public ProxyMockSpan span() {
      final Span mockSpan = mockScope.span();
      final Span realSpan = mockSpan == null ? null : Objects.requireNonNull(realScope.span());
      if (span == null || span.mockSpan != mockSpan)
        span = new ProxyMockSpan(tracer, (MockSpan)mockSpan, realSpan);
      else if (span.realSpan != realSpan)
        span.realSpan = realSpan;

      return span;
    }

    static class ProxyMockSpan extends MockSpan {
      private final Span mockSpan;
      private Span realSpan;

      ProxyMockSpan(final ProxyMockTracer tracer, final MockSpan mockSpan, final Span realSpan) {
        super(tracer, mockSpan.operationName(), mockSpan.startMicros(), mockSpan.tags, mockSpan.references);
        this.mockSpan = mockSpan;
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
        super.finish(nowMicros());
        realSpan.finish();
      }

      @Override
      public void finish(final long finishMicros) {
        super.finish(finishMicros);
        realSpan.finish(finishMicros);
      }
    }
  }
}