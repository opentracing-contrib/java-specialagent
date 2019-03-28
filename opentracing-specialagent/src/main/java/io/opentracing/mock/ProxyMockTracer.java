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

import io.opentracing.Scope;
import io.opentracing.ScopeManager;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.mock.MockSpan.MockContext;
import io.opentracing.noop.NoopScopeManager;
import io.opentracing.propagation.Format;
import io.opentracing.util.ThreadLocalScopeManager;

public class ProxyMockTracer extends MockTracer {
  final Tracer realTracer;
  private ProxyMockScopeManager scopeManager;

  public ProxyMockTracer(final Tracer tracer) {
    super(new ThreadLocalScopeManager(), Propagator.TEXT_MAP);
    this.realTracer = tracer;
    if (tracer == this)
      throw new IllegalArgumentException(ProxyMockTracer.class.getSimpleName() + " cannot proxy to itself");
  }

  public ProxyMockTracer(final Tracer tracer, final ScopeManager scopeManager) {
    super(scopeManager, Propagator.TEXT_MAP);
    this.realTracer = tracer;
    if (tracer == this)
      throw new IllegalArgumentException(ProxyMockTracer.class.getSimpleName() + " cannot proxy to itself");
  }

  public ProxyMockTracer(final Tracer tracer, final ScopeManager scopeManager, final Propagator propagator) {
    super(scopeManager, propagator);
    this.realTracer = tracer;
    if (tracer == this)
      throw new IllegalArgumentException(ProxyMockTracer.class.getSimpleName() + " cannot proxy to itself");
  }

  public ProxyMockTracer(final Tracer tracer, final Propagator propagator) {
    super(NoopScopeManager.INSTANCE, propagator);
    this.realTracer = tracer;
    if (tracer == this)
      throw new IllegalArgumentException(ProxyMockTracer.class.getSimpleName() + " cannot proxy to itself");
  }

  @Override
  public ProxyMockScopeManager scopeManager() {
    final ScopeManager mockScopeManager = super.scopeManager();
    final ScopeManager realScopeManager = realTracer.scopeManager();
    if (scopeManager == null || scopeManager.mockScopeManager != mockScopeManager || scopeManager.realScopeManager != realScopeManager)
      scopeManager = new ProxyMockScopeManager(mockScopeManager, realScopeManager);

    return scopeManager;
  }

  @Override
  public ProxyMockSpanBuilder buildSpan(final String operationName) {
    final Tracer.SpanBuilder realSpanBuilder = realTracer.buildSpan(operationName);
    return new ProxyMockSpanBuilder(operationName, realSpanBuilder);
  }

  class ProxyMockSpanBuilder extends MockTracer.SpanBuilder {
    final Tracer.SpanBuilder realSpanBuilder;

    public ProxyMockSpanBuilder(final String operationName, final Tracer.SpanBuilder realSpanBuilder) {
      super(operationName);
      this.realSpanBuilder = realSpanBuilder;
    }

    @Override
    public ProxyMockSpanBuilder asChildOf(final SpanContext parent) {
      super.asChildOf(parent);
      realSpanBuilder.asChildOf(parent);
      return this;
    }

    @Override
    public ProxyMockSpanBuilder asChildOf(final Span parent) {
      super.asChildOf(parent);
      realSpanBuilder.asChildOf(parent);
      return this;
    }

    @Override
    public ProxyMockSpanBuilder addReference(final String referenceType, final SpanContext referencedContext) {
      super.addReference(referenceType, referencedContext);
      realSpanBuilder.addReference(referenceType, referencedContext);
      return this;
    }

    @Override
    public ProxyMockSpanBuilder ignoreActiveSpan() {
      super.ignoreActiveSpan();
      realSpanBuilder.ignoreActiveSpan();
      return this;
    }

    @Override
    public ProxyMockSpanBuilder withTag(final String key, final String value) {
      super.withTag(key, value);
      realSpanBuilder.withTag(key, value);
      return this;
    }

    @Override
    public ProxyMockSpanBuilder withTag(final String key, final boolean value) {
      super.withTag(key, value);
      realSpanBuilder.withTag(key, value);
      return this;
    }

    @Override
    public ProxyMockSpanBuilder withTag(final String key, final Number value) {
      super.withTag(key, value);
      realSpanBuilder.withTag(key, value);
      return this;
    }

    @Override
    public ProxyMockSpanBuilder withStartTimestamp(final long microseconds) {
      super.withStartTimestamp(microseconds);
      realSpanBuilder.withStartTimestamp(microseconds);
      return this;
    }

    @Override
    public ProxyMockScope startActive(final boolean finishSpanOnClose) {
      final Scope mockScope = super.startActive(finishSpanOnClose);
      final Scope realScope = realSpanBuilder.startActive(finishSpanOnClose);
      return new ProxyMockScope(mockScope, realScope);
    }

    @Override
    @Deprecated
    public ProxyMockSpan startManual() {
      final MockSpan mockSpan = super.startManual();
      final Span realSpan = realSpanBuilder.startManual();
      return new ProxyMockSpan(mockSpan, realSpan);
    }

    @Override
    public ProxyMockSpan start() {
      final MockSpan mockSpan = super.start();
      final Span realSpan = realSpanBuilder.start();
      return new ProxyMockSpan(mockSpan, realSpan);
    }
  }

  @Override
  public <C>void inject(final SpanContext spanContext, final Format<C> format, final C carrier) {
    super.inject(spanContext, format, carrier);
    realTracer.inject(((ProxyMockSpanContext)spanContext).realSpanContext, format, carrier);
  }

  @Override
  public <C>ProxyMockSpanContext extract(final Format<C> format, final C carrier) {
    final SpanContext mockSpanContext = super.extract(format, carrier);
    final SpanContext realSpanContext = realTracer.extract(format, carrier);
    return new ProxyMockSpanContext((MockContext)mockSpanContext, realSpanContext);
  }

  @Override
  public Span activeSpan() {
    final Span mockSpan = super.activeSpan();
    final Span realSpan = realTracer.activeSpan();
    if (mockSpan == null ? realSpan != null : realSpan == null)
      throw new IllegalStateException();

    return mockSpan == null ? null : new ProxyMockSpan((MockSpan)mockSpan, realSpan);
  }
}