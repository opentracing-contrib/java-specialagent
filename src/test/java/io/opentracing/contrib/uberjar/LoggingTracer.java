package io.opentracing.contrib.uberjar;

import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.opentracing.Scope;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.contrib.audit.AuditScopeManager;
import io.opentracing.contrib.audit.AuditSpan;
import io.opentracing.contrib.audit.AuditSpanBuilder;
import io.opentracing.contrib.audit.AuditTracer;
import io.opentracing.propagation.Format;

public class LoggingTracer extends AuditTracer {
  private static final Logger logger = Logger.getLogger(LoggingTracer.class.getName());
  private static final Level level = Level.INFO;

  private static final Set<Class<?>> wrappedPrimitives = new HashSet<>();

  static {
    wrappedPrimitives.add(Boolean.class);
    wrappedPrimitives.add(Byte.class);
    wrappedPrimitives.add(Short.class);
    wrappedPrimitives.add(Integer.class);
    wrappedPrimitives.add(Long.class);
    wrappedPrimitives.add(Float.class);
    wrappedPrimitives.add(Double.class);
  }

  private static String args(final Object ... objects) {
    final StringBuilder builder = new StringBuilder();
    for (int i = 0; i < objects.length; ++i) {
      if (i > 0)
        builder.append(", ");

      final Object obj = objects[i];
      if (obj == null)
        builder.append("null");
      else if (obj.getClass() == String.class)
        builder.append('"').append(obj).append('"');
      else if (obj.getClass() == Character.class)
        builder.append('\'').append(obj).append('\'');
      else if (wrappedPrimitives.contains(obj.getClass()))
        builder.append(obj);
      else
        builder.append(obj.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(obj)));
    }

    return builder.toString();
  }

  public LoggingTracer() {
    super(new Callback() {
      @Override
      public void onBuildSpan(final Tracer tracer, final String operationName) {
        logger.log(level, operationName);
      }

      @Override
      public <C> void onInject(final SpanContext spanContext, final Format<C> format, final C carrier) {
        logger.log(level, args(format, carrier));
      }

      @Override
      public <C> void onExtract(final SpanContext spanContext, final Format<C> format, final C carrier) {
        logger.log(level, args(format, carrier));
      }
    }, new AuditScopeManager.Callback() {
      @Override
      public void onActivate(final Scope scope) {
        logger.log(level, args(scope));
      }
    }, new AuditSpanBuilder.Callback() {
      @Override
      public void onAddReference(final AuditSpanBuilder spanBuilder, final String refType, final SpanContext referencedContext) {
        logger.log(level, args(spanBuilder, refType, referencedContext));
      }

      @Override
      public void onIgnoreActiveSpan(final AuditSpanBuilder spanBuilder) {
        logger.log(level, args(spanBuilder));
      }

      @Override
      public void onWithStartTimestamp(final AuditSpanBuilder spanBuilder, final long startMicros) {
        logger.log(level, args(spanBuilder, startMicros));
      }

      @Override
      public void onWithTag(final AuditSpanBuilder spanBuilder, final String key, final Object value) {
        logger.log(level, args(spanBuilder, key, value));
      }

      @Override
      public void onStart(final AuditSpanBuilder spanBuilder, final boolean active, final boolean finishOnClose) {
        logger.log(level, args(spanBuilder, active, finishOnClose));
      }
    }, new AuditSpan.Callback() {
      @Override
      public void onStart(final AuditSpan span) {
        logger.log(level, args(span));
      }

      @Override
      public void onSetBaggageItem(final AuditSpan span, final String key, final String value) {
        logger.log(level, args(span, key, value));
      }

      @Override
      public void onSetTag(final AuditSpan span, final String key, final Object value) {
        logger.log(level, args(span, key, value));
      }

      @Override
      public void onLog(final AuditSpan span, final long timestampMicroseconds, final Object entry) {
        logger.log(level, args(span, timestampMicroseconds, entry));
      }

      @Override
      public void onSetOperationName(final AuditSpan span, final String operationName) {
        logger.log(level, args(span, operationName));
      }

      @Override
      public void onFinish(final AuditSpan span, final long finishMicros) {
        logger.log(level, args(span, finishMicros));
      }
    });
  }
}