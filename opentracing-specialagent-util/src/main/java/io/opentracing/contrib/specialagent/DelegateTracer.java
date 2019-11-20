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

import java.util.Objects;

import io.opentracing.Scope;
import io.opentracing.ScopeManager;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;

/**
 * A {@link DelegateTracer} contains some other {@link Tracer}, possibly
 * transforming the method parameters along the way or providing additional
 * functionality. The class {@link DelegateTracer} itself simply overrides all
 * methods of {@link Tracer} with versions that delegate all calls to the source
 * {@link Tracer}. Subclasses of {@link DelegateTracer} may further override
 * some of these methods and may also provide additional methods and fields.
 *
 * @author Seva Safris
 */
public class DelegateTracer implements Tracer {
  /** The target {@link Tracer}. */
  protected volatile Tracer target;

  /**
   * Creates a new {@link DelegateTracer} with the specified target
   * {@link Tracer}.
   *
   * @param target The target {@link Tracer}.
   * @throws NullPointerException If the target {@link Tracer} is null.
   */
  public DelegateTracer(final Tracer target) {
    this.target = Objects.requireNonNull(target);
  }

  /**
   * Creates a new {@link DelegateTracer} with a null target.
   */
  protected DelegateTracer() {
  }

  @Override
  public ScopeManager scopeManager() {
    return target.scopeManager();
  }

  @Override
  public Span activeSpan() {
    return target.activeSpan();
  }

  @Override
  public Scope activateSpan(final Span span) {
    return target.activateSpan(span);
  }

  @Override
  public SpanBuilder buildSpan(final String operationName) {
    return target.buildSpan(operationName);
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

  @Override
  public boolean equals(final Object obj) {
    if (obj == this)
      return true;

    if (!(obj instanceof DelegateTracer))
      return false;

    final DelegateTracer that = (DelegateTracer)obj;
    return target != null ? target.equals(that.target) : that.target == null;
  }

  @Override
  public int hashCode() {
    return target == null ? 733 : target.hashCode();
  }

  @Override
  public String toString() {
    return String.valueOf(target);
  }
}