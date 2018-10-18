/*
 * Copyright 2017 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.opentracing.contrib.instrumenter;

import java.net.HttpURLConnection;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jboss.byteman.rule.Rule;
import org.jboss.byteman.rule.helper.Helper;

import io.opentracing.Scope;
import io.opentracing.ScopeManager;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.Tracer.SpanBuilder;
import io.opentracing.contrib.tracerresolver.TracerResolver;
import io.opentracing.propagation.Format;
import io.opentracing.util.GlobalTracer;

/**
 * This class provides helper capabilities to the byteman rules.
 */
public class OpenTracingHelper extends Helper {
  private static final Logger logger = Logger.getLogger(OpenTracingHelper.class.getName());

  private static final Map<Object,Span> spanAssociations = Collections.synchronizedMap(new WeakHashMap<Object,Span>());
  private static final Map<Object,Span> finished = Collections.synchronizedMap(new WeakHashMap<Object,Span>());
  private static final Map<Object,Integer> state = Collections.synchronizedMap(new WeakHashMap<Object,Integer>());

  private static final Object tracerMutex = new Object();
  private static Tracer tracer;

  public OpenTracingHelper(final Rule rule) {
    super(rule);
  }

  /**
   * If the property
   * {@code 'io.opentracing.contrib.agent.allowInstrumentedTracer'} ==
   * {@code "true"}, then rules will be allowed to execute in the tracer
   * initialization logic. This can be problematic if those rules also
   * initialize the tracer (which could happen especially if it is the first
   * rule to execute) since they will happen in the same thread and the locking
   * will not apply.
   */
  private static boolean allowInstrumentedTracer() {
    return Boolean.parseBoolean(System.getProperty("io.opentracing.contrib.agent.allowInstrumentedTracer"));
  }

  /**
   * Returns the OpenTracing tracer.
   *
   * @return The OpenTracing tracer.
   */
  public Tracer getTracer() {
    if (tracer != null)
      return tracer;

    synchronized (tracerMutex) {
      if (tracer != null)
        return tracer;

      if (!GlobalTracer.isRegistered()) {
        final boolean triggeringState = Rule.isTriggeringEnabled();
        if (!allowInstrumentedTracer() && triggeringState) {
          // Temporarily disable triggering of rules unless we have explicitly
          // allowed the tracer to be instrumented.
          setTriggering(false);
        }

        try {
          // Try to obtain a tracer using the TracerResolver
          final Tracer resolved = TracerResolver.resolveTracer();
          if (resolved != null) {
            try {
              GlobalTracer.register(resolved);
            }
            catch (final RuntimeException e) {
              logger.log(Level.WARNING, "Failed to register tracer '" + resolved + "'", e);
            }
          }
        }
        finally {
          setTriggering(triggeringState);
        }
      }

      // Initialize the tracer even if one has not been registered
      // (i.e. it will use a NoopTracer under the covers)
      return tracer = new InstrumenterTracer(GlobalTracer.get());
    }
  }

  /**
   * This method establishes an association between an application object (i.e.
   * the subject of the instrumentation) and a span. Once the application object
   * is no longer being used, the association with the span will automatically
   * be discarded.
   *
   * @param obj The application object to be associated with the span.
   * @param span The span.
   */
  public void associateSpan(final Object obj, final Span span) {
    spanAssociations.put(obj, span);
  }

  /**
   * This method retrieves the span associated with the supplied application
   * object.
   *
   * @param obj The application object.
   * @return The span, or null if no associated span exists.
   */
  public Span retrieveSpan(final Object obj) {
    return spanAssociations.get(obj);
  }

  /**********************************************/
  /** Needs to be replaced by span.isFinished() */
  public void finishedSpan(final Object key, final Span span) {
    finished.put(key, span);
  }

  public boolean isFinished(final Object key) {
    return finished.containsKey(key);
  }

  /**********************************************/

  /**
   * This method enables an instrumentation rule to record a 'state' number
   * against an application object. This can be used in situations where rules
   * are only applicable in certain states. For example, the simple case for
   * rules responsible for installing filters would be states representing
   * NOT_INSTALLED and INSTALLED. This means that the filter would only be
   * installed if the application object (target of the instrumentation rule)
   * was in the NOT_INSTALLED state. However other more complex scenarios may be
   * encountered where more than two states are required.
   *
   * @param obj The application object
   * @param value The state value
   */
  public void setState(final Object obj, final int value) {
    state.put(obj, value);
  }

  /**
   * This method retrieves the current 'state' number associated with the
   * supplied application object.
   *
   * @param obj The application object
   * @return The state, or 0 if no state currently exists
   */
  public int getState(final Object obj) {
    final Integer value = state.get(obj);
    return value == null ? 0 : value.intValue();
  }

  /**
   * This method determines whether the instrumentation point, associated with
   * the supplied object, should be ignored.
   *
   * @param obj The instrumentation target
   * @return Whether the instrumentation point should be ignored
   */
  public boolean ignore(final Object obj) {
    boolean ignore = false;

    if (obj instanceof HttpURLConnection) {
      final String value = ((HttpURLConnection)obj).getRequestProperty("opentracing.ignore");
      ignore = value != null && value.equalsIgnoreCase("true");
    }

    // TODO: If other technologies need to use this feature,
    // then create an Adapter to wrap the specifics of each
    // technology and provide access to their properties
    if (ignore && logger.isLoggable(Level.FINEST)) {
      logger.finest("Ignoring request because the property [opentracing.ignore] is present.");
    }

    return ignore;
  }

  /**
   * Proxy tracer used for one purpose - to enable the rules to define a ChildOf
   * relationship without being concerned whether the supplied Span is null. If
   * the spec (and Tracer implementations) are updated to indicate a null should
   * be ignored, then this proxy can be removed.
   */
  public static class InstrumenterTracer implements Tracer {
    private final Tracer tracer;

    public InstrumenterTracer(final Tracer tracer) {
      System.err.println("new InstrumenterTracer(" + tracer + ")");
      System.err.println("  ClassLoader: " + tracer.getClass().getClassLoader() + " " + ClassLoader.getSystemClassLoader().getResource(tracer.getClass().getName().replace('.', '/').concat(".class")));
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
    public <C>void inject(final SpanContext ctx, final Format<C> format, final C carrier) {
      tracer.inject(ctx, format, carrier);
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
    public SpanBuilder addReference(final String type, final SpanContext ctx) {
      if (ctx != null)
        spanBuilder.addReference(type, ctx);

      return this;
    }

    @Override
    public SpanBuilder asChildOf(final SpanContext ctx) {
      if (ctx != null)
        spanBuilder.asChildOf(ctx);

      return this;
    }

    @Override
    public SpanBuilder asChildOf(final Span span) {
      if (span != null)
        spanBuilder.asChildOf(span);

      return this;
    }

    @Override
    public Span start() {
      return spanBuilder.start();
    }

    @Override
    public SpanBuilder withStartTimestamp(final long ts) {
      spanBuilder.withStartTimestamp(ts);
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