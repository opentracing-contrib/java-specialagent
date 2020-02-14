package io.opentracing.contrib.specialagent.tracer;

import io.opentracing.Tracer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class OperationNameCustomizer implements SpanCustomizer {

  private List<Map<String, Object>> log;
  private Map<String, Object> tags;
  private String operationName;

  public OperationNameCustomizer(String operationName) {
    this.operationName = operationName;
  }

  @Override
  public void setTag(String key, Object value) {
    if (tags == null) {
      tags = new LinkedHashMap<>();
    }
    tags.put(key, value);
  }

  @Override
  public void addLogField(String key, Object value) {
    if (log == null) {
      log = new ArrayList<>();
    }
    log.add(Collections.singletonMap(key, value));
  }

  @Override
  public void setOperationName(String name) {
    operationName = name;
  }

  public Tracer.SpanBuilder buildSpan(Tracer target, SpanRules rules) {
    rules.processOperationName(operationName, this);

    return new CustomizableSpanBuilder(target.buildSpan(operationName), rules, tags, log);
  }
}
