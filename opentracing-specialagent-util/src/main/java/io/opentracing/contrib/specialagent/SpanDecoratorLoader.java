package io.opentracing.contrib.specialagent;

import java.util.Collections;
import java.util.List;

public class SpanDecoratorLoader<SD> {
  public static final String DECORATOR_SEPARATOR = ",";
  public static final String SPAN_DECORATORS_OPT_PREFIX = "sa.integration.";
  public static final String SPAN_DECORATORS_CLASSPATH_OPT_SURFIX = ".spanDecorators.classpath";
  public static final String SPAN_DECORATORS_OPT_SURFIX = ".spanDecorators";

  private String spanDecoratorClasspathOpt;
  private String spanDecoratorsOpt;
  private Class<SD> spanDecoratorClass;

  public static <SD> SpanDecoratorLoader<SD> newInstance(String ruleName, Class<SD> spanDecoratorClass) {
    return new SpanDecoratorLoader<>(ruleName, spanDecoratorClass);
  }

  private SpanDecoratorLoader(String ruleName, Class<SD> spanDecoratorClass) {
    this.spanDecoratorClass = spanDecoratorClass;
    this.spanDecoratorClasspathOpt = SPAN_DECORATORS_OPT_PREFIX + ruleName + SPAN_DECORATORS_CLASSPATH_OPT_SURFIX;
    this.spanDecoratorsOpt = SPAN_DECORATORS_OPT_PREFIX + ruleName + SPAN_DECORATORS_OPT_SURFIX;
  }

  public List<SD> getSpanDecorators(SD defultSpanDecorator) {
    final String spanDecoratorsArgs = System.getProperty(spanDecoratorsOpt);
    String[] spanDecoratorNames = null;
    if (spanDecoratorsArgs != null) {
      spanDecoratorNames = spanDecoratorsArgs.split(DECORATOR_SEPARATOR);
    }
    final List<SD> spanDecorators = InstrumentIntegration.getInstance().getExtensionInstances(this.spanDecoratorClass,
        this.spanDecoratorClasspathOpt, spanDecoratorNames);
    if (defultSpanDecorator != null && (spanDecorators == null || spanDecorators.isEmpty())) {
      return Collections.singletonList(defultSpanDecorator);
    }
    return spanDecorators;
  }
}
