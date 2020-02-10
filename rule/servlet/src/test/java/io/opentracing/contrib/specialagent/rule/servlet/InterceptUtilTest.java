package io.opentracing.contrib.specialagent.rule.servlet;

import io.opentracing.contrib.web.servlet.filter.ServletFilterSpanDecorator;
import io.opentracing.contrib.web.servlet.filter.StandardTagsServletFilterSpanDecorator;
import io.opentracing.contrib.web.servlet.filter.decorator.HttpHeaderServletFilterSpanDecorator;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.ListAssert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class InterceptUtilTest {

  @Test
  public void explicitStandardTags() {
    parseDecorators("io.opentracing.contrib.specialagent.rule.servlet.MockSpanDecorator," +
      "io.opentracing.contrib.web.servlet.filter.StandardTagsServletFilterSpanDecorator").containsExactly(
      MockSpanDecorator.class,
      StandardTagsServletFilterSpanDecorator.class,
      HttpHeaderServletFilterSpanDecorator.class
    );
  }

  @Test
  public void implicitStandardTags() {
    parseDecorators(null).containsExactly(
      StandardTagsServletFilterSpanDecorator.class,
      HttpHeaderServletFilterSpanDecorator.class
    );
  }

  private ListAssert<Class<?>> parseDecorators(String spanDecoratorsArgs) {
    List<ServletFilterSpanDecorator> decorators = InterceptUtil.parseSpanDecorators(spanDecoratorsArgs);
    List<Class<?>> list = new ArrayList<>();
    for (ServletFilterSpanDecorator decorator : decorators) {
      list.add(decorator.getClass());
    }
    return Assertions.assertThat(list);
  }
}
