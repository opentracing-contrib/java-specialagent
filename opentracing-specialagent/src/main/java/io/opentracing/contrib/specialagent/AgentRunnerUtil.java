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
import java.util.logging.Level;
import java.util.logging.Logger;

import io.opentracing.Scope;
import io.opentracing.ScopeManager;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.Tracer.SpanBuilder;
import io.opentracing.mock.MockTracer;
import io.opentracing.propagation.Format;
import io.opentracing.util.GlobalTracer;

/**
 * Support utility methods for {@code AgentRunner}. This class is deliberately
 * placed in the main path, because it is supposed to be loaded in the bootstrap
 * class loader.
 *
 * @author Seva Safris
 */
public class AgentRunnerUtil {
  private static final Logger logger = Logger.getLogger(AgentRunnerUtil.class.getName());
  private static Tracer tracer = null;
  private static final Object tracerMutex = new Object();

  /**
   * Returns the OpenTracing {@link Tracer} to be used for the duration of the
   * test process. The {@link Tracer} is initialized on first invocation to this
   * method in a synchronized, thread-safe manner. If the {@code "-javaagent"}
   * argument is not specified for the current process, this function will
   * return {@code null}.
   *
   * @return The OpenTracing {@link Tracer} to be used for the duration of the
   *         test process, or {@code null} if the {@code "-javaagent"} argument
   *         is not specified for the current process.
   */
  public static Tracer getTracer() {
    if (tracer != null)
      return tracer;

    synchronized (tracerMutex) {
      if (tracer != null)
        return tracer;

      final Tracer tracer = GlobalTracer.isRegistered() ? GlobalTracer.get() : new MockTracer();
      if (logger.isLoggable(Level.FINEST)) {
        logger.finest("Registering tracer for AgentRunner: " + tracer);
        logger.finest("  Tracer ClassLoader: " + tracer.getClass().getClassLoader());
        logger.finest("  Tracer Location: " + ClassLoader.getSystemClassLoader().getResource(tracer.getClass().getName().replace('.', '/').concat(".class")));
        logger.finest("  GlobalTracer ClassLoader: " + GlobalTracer.class.getClassLoader());
        logger.finest("  GlobalTracer Location: " + ClassLoader.getSystemClassLoader().getResource(GlobalTracer.class.getName().replace('.', '/').concat(".class")));
      }

      if (!(tracer instanceof GlobalTracer))
        GlobalTracer.register(tracer);

      return AgentRunnerUtil.tracer = tracer instanceof MockTracer ? tracer : new ProxyTracer(tracer);
    }
  }

  /**
   * Proxy tracer used for one purpose - to enable the rules to define a ChildOf
   * relationship without being concerned whether the supplied Span is null. If
   * the spec (and Tracer implementations) are updated to indicate a null should
   * be ignored, then this proxy can be removed.
   */
  public static class ProxyTracer implements Tracer {
    private final Tracer tracer;

    public ProxyTracer(final Tracer tracer) {
      if (logger.isLoggable(Level.FINEST)) {
        logger.finest("new " + ProxyTracer.class.getSimpleName() + "(" + tracer + ")");
        logger.finest("  ClassLoader: " + tracer.getClass().getClassLoader());
        logger.finest("  Location: " + ClassLoader.getSystemClassLoader().getResource(tracer.getClass().getName().replace('.', '/').concat(".class")));
      }

      this.tracer = Objects.requireNonNull(tracer);
    }

    @Override
    public SpanBuilder buildSpan(final String operation) {
      return new AgentSpanBuilder(tracer.buildSpan(operation));
    }

    @Override
    public <C>SpanContext extract(final Format<C> format, final C carrier) {
      return tracer.extract(format, carrier);
    }

    @Override
    public <C>void inject(final SpanContext spanContext, final Format<C> format, final C carrier) {
      tracer.inject(spanContext, format, carrier);
    }

    @Override
    public Span activeSpan() {
      return tracer.activeSpan();
    }

    @Override
    public ScopeManager scopeManager() {
      return tracer.scopeManager();
    }
  }

  public static class AgentSpanBuilder implements SpanBuilder {
    private final SpanBuilder spanBuilder;

    public AgentSpanBuilder(final SpanBuilder spanBuilder) {
      this.spanBuilder = Objects.requireNonNull(spanBuilder);
    }

    @Override
    public SpanBuilder addReference(final String referenceType, final SpanContext referencedContext) {
      if (referencedContext != null)
        spanBuilder.addReference(referenceType, referencedContext);

      return this;
    }

    @Override
    public SpanBuilder asChildOf(final SpanContext parent) {
      if (parent != null)
        spanBuilder.asChildOf(parent);

      return this;
    }

    @Override
    public SpanBuilder asChildOf(final Span parent) {
      if (parent != null)
        spanBuilder.asChildOf(parent);

      return this;
    }

    @Override
    public Span start() {
      return spanBuilder.start();
    }

    @Override
    public SpanBuilder withStartTimestamp(final long microseconds) {
      spanBuilder.withStartTimestamp(microseconds);
      return this;
    }

    @Override
    public SpanBuilder withTag(final String name, final String value) {
      spanBuilder.withTag(name, value);
      return this;
    }

    @Override
    public SpanBuilder withTag(final String name, final boolean value) {
      spanBuilder.withTag(name, value);
      return this;
    }

    @Override
    public SpanBuilder withTag(final String name, final Number value) {
      spanBuilder.withTag(name, value);
      return this;
    }

    @Override
    public SpanBuilder ignoreActiveSpan() {
      spanBuilder.ignoreActiveSpan();
      return this;
    }

    @Override
    public Scope startActive(final boolean finishOnClose) {
      return spanBuilder.startActive(finishOnClose);
    }

    @Override
    @Deprecated
    public Span startManual() {
      return spanBuilder.startManual();
    }
  }
}