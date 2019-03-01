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

import io.opentracing.ScopeManager;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.mock.MockTracer;
import io.opentracing.noop.NoopScopeManager;
import io.opentracing.propagation.Format;
import io.opentracing.util.ThreadLocalScopeManager;

public class ProxyMockTracer extends MockTracer {
  final Tracer realTracer;
  private ProxyMockScopeManager scopeManager;
  private ProxyMockSpanBuilder spanBuilder;

  public ProxyMockTracer(final Tracer tracer) {
    super(new ThreadLocalScopeManager(), Propagator.TEXT_MAP);
    this.realTracer = tracer;
  }

  public ProxyMockTracer(final Tracer tracer, final ScopeManager scopeManager) {
    super(scopeManager, Propagator.TEXT_MAP);
    this.realTracer = tracer;
  }

  public ProxyMockTracer(final Tracer tracer, final ScopeManager scopeManager, final Propagator propagator) {
    super(scopeManager, propagator);
    this.realTracer = tracer;
  }

  public ProxyMockTracer(final Tracer tracer, final Propagator propagator) {
    super(NoopScopeManager.INSTANCE, propagator);
    this.realTracer = tracer;
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
    final Tracer.SpanBuilder mockSpanBuilder = super.buildSpan(operationName);
    final Tracer.SpanBuilder realSpanBuilder = realTracer.buildSpan(operationName);
    if (spanBuilder == null || spanBuilder.mockSpanBuilder != mockSpanBuilder || spanBuilder.realSpanBuilder != realSpanBuilder)
      spanBuilder = new ProxyMockSpanBuilder(mockSpanBuilder, realSpanBuilder);

    return spanBuilder;
  }

  @Override
  public <C>void inject(final SpanContext spanContext, final Format<C> format, final C carrier) {
    super.inject(spanContext, format, carrier);
    realTracer.inject(spanContext, format, carrier);
  }

  @Override
  public <C>ProxyMockSpanContext extract(final Format<C> format, final C carrier) {
    final SpanContext mockSpanContext = super.extract(format, carrier);
    final SpanContext realSpanContext = realTracer.extract(format, carrier);
    return new ProxyMockSpanContext(mockSpanContext, realSpanContext);
  }

  @Override
  public Span activeSpan() {
    final Span mockSpan = super.activeSpan();
    final Span realSpan = realTracer.activeSpan();
    return new ProxyMockSpan(mockSpan, realSpan);
  }
}