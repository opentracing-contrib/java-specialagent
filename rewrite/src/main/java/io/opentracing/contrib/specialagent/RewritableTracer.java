/* Copyright 2020 The OpenTracing Authors
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
import io.opentracing.ScopeManager;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;

public class RewritableTracer implements Tracer {
  final Tracer target;
  final RewriteRules rules;

  RewritableTracer(final Tracer target, final RewriteRules rules) {
    this.target = target;
    this.rules = rules;
  }

  @Override
  public ScopeManager scopeManager() {
    return target.scopeManager();
  }

  private RewritableSpan activeSpan;

  @Override
  public Span activeSpan() {
    final Span activeSpan = target.activeSpan();
    return this.activeSpan == null || this.activeSpan.target != activeSpan ? this.activeSpan = new RewritableSpan(activeSpan, rules) : this.activeSpan;
  }

  @Override
  public Scope activateSpan(final Span span) {
    return target.activateSpan(span);
  }

  private RewritableSpanBuilder spanBuilder;

  @Override
  public SpanBuilder buildSpan(final String operationName) {
    final SpanBuilder spanBuilder = target.buildSpan(operationName);
    return this.spanBuilder == null || this.spanBuilder.target != spanBuilder ? this.spanBuilder = new RewritableSpanBuilder(operationName, spanBuilder, rules) : this.spanBuilder;
  }

  @Override
  public <C>void inject(final SpanContext spanContext, final Format<C> format, final C carrier) {
    target.inject(spanContext, format, carrier);
  }

  @Override
  public <C>SpanContext extract(final Format<C> format, final C carrier) {
    return target.extract(format, carrier);
  }

  @Override
  public void close() {
    target.close();
  }
}