package io.opentracing.contrib.specialagent.tracer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.opentracing.Tracer;

public class OperationNameCustomizer implements SpanCustomizer {
  private List<Map<String,Object>> log;
  private Map<String,Object> tags;
  private String operationName;

  public OperationNameCustomizer(final String operationName) {
    this.operationName = operationName;
  }

  @Override
  public void setTag(final String key, final Object value) {
    if (tags == null)
      tags = new LinkedHashMap<>();

    tags.put(key, value);
  }

  @Override
  public void addLogField(final String key, final Object value) {
    if (log == null)
      log = new ArrayList<>();

    log.add(Collections.singletonMap(key, value));
  }

  @Override
  public void setOperationName(final String name) {
    operationName = name;
  }

  public Tracer.SpanBuilder buildSpan(final Tracer target, final SpanRules rules) {
    rules.processOperationName(operationName, this);
    return new CustomizableSpanBuilder(target.buildSpan(operationName), rules, tags, log);
  }
}