package io.opentracing.contrib.specialagent.tracer;

import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.tag.Tag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class CustomizableSpanBuilder implements Tracer.SpanBuilder {
  private final Tracer.SpanBuilder target;
  private final SpanRules rules;
  private List<Map<String, Object>> log;
  private String operationName;
  private SpanCustomizer customizer = new SpanCustomizer() {
    @Override
    public void setTag(String key, Object value) {
      if (value == null) {
        target.withTag(key, (String) null);
      } else if (value instanceof Number) {
        target.withTag(key, (Number) value);
      } else if (value instanceof Boolean) {
        target.withTag(key, (Boolean) value);
      } else {
        target.withTag(key, value.toString());
      }
    }

    @Override
    public void setOperationName(String name) {
      operationName = name;
    }

    @Override
    public void addLogField(String key, Object value) {
      if (log == null) {
        log = new ArrayList<>();
      }
      log.add(Collections.singletonMap(key, value));
    }
  };

  public CustomizableSpanBuilder(Tracer.SpanBuilder target, SpanRules rules,
                                 Map<String, Object> tags, List<Map<String, Object>> log) {
    this.target = target;
    this.rules = rules;
    this.log = log;

    if (tags != null) {
      for (Map.Entry<String, Object> entry : tags.entrySet()) {
        customizer.setTag(entry.getKey(), entry.getValue());
      }
    }
  }

  @Override
  public Tracer.SpanBuilder asChildOf(SpanContext parent) {
    target.asChildOf(parent);
    return this;
  }

  @Override
  public Tracer.SpanBuilder asChildOf(Span parent) {
    target.asChildOf(parent);
    return this;
  }

  @Override
  public Tracer.SpanBuilder addReference(String referenceType, SpanContext referencedContext) {
    target.addReference(referenceType, referencedContext);
    return this;
  }

  @Override
  public Tracer.SpanBuilder ignoreActiveSpan() {
    target.ignoreActiveSpan();
    return this;
  }

  @Override
  public Tracer.SpanBuilder withTag(String key, String value) {
    rules.setTag(key, value, customizer);
    return this;
  }

  @Override
  public Tracer.SpanBuilder withTag(String key, boolean value) {
    rules.setTag(key, value, customizer);
    return this;
  }

  @Override
  public Tracer.SpanBuilder withTag(String key, Number value) {
    rules.setTag(key, value, customizer);
    return this;
  }

  @Override
  public <T> Tracer.SpanBuilder withTag(Tag<T> tag, T value) {
    rules.setTag(tag.getKey(), value, customizer);
    return this;
  }

  @Override
  public Tracer.SpanBuilder withStartTimestamp(long microseconds) {
    target.withStartTimestamp(microseconds);
    return this;
  }

  @Override
  @Deprecated
  public Span startManual() {
    return target.startManual();
  }

  @Override
  public Span start() {
    Span span = target.start();
    if (log != null) {
      for (Map<String, Object> fields : log) {
        span.log(fields);
      }
    }
    if (operationName != null) {
      span.setOperationName(operationName);
    }
    return new CustomizableSpan(span, rules);
  }

  @Override
  @Deprecated
  public Scope startActive(boolean finishSpanOnClose) {
    return target.startActive(finishSpanOnClose);
  }
}
