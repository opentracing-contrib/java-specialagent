package io.opentracing.contrib.specialagent.rule.apache.httpclient;

import static org.junit.Assert.*;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class ConfigurationTest {

  @Test
  public void testExplicitSpanDecorators() {
    testDecorators(ApacheClientSpanDecorator.StandardTags.class.getName() + "," + MockSpanDecorator.class.getName(),
        ApacheClientSpanDecorator.StandardTags.class, MockSpanDecorator.class);
  }

  @Test
  public void testImplicitSpanDecorators() {
    testDecorators(null, ApacheClientSpanDecorator.StandardTags.class);
  }

  private static void testDecorators(final String spanDecoratorsArgs, final Class<?>... expecteds) {
    final List<ApacheClientSpanDecorator> decorators = Configuration.parseSpanDecorators(spanDecoratorsArgs);
    assertEquals(expecteds.length, decorators.size());
    final List<Class<?>> list = Arrays.asList(expecteds);
    for (final ApacheClientSpanDecorator decorator : decorators)
      assertTrue(list.contains(decorator.getClass()));
  }
}
