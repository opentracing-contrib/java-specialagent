/* Copyright 2020 The OpenTracing Authors
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

package io.opentracing.contrib.specialagent.rule.servlet;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import io.opentracing.contrib.web.servlet.filter.ServletFilterSpanDecorator;
import io.opentracing.contrib.web.servlet.filter.StandardTagsServletFilterSpanDecorator;
import io.opentracing.contrib.web.servlet.filter.decorator.ServletFilterHeaderSpanDecorator;

public class InterceptUtilTest {
  @Test
  public void explicitStandardTags() {
    testDecorators("io.opentracing.contrib.specialagent.rule.servlet.MockSpanDecorator," +
      "io.opentracing.contrib.web.servlet.filter.StandardTagsServletFilterSpanDecorator",
      MockSpanDecorator.class,
      StandardTagsServletFilterSpanDecorator.class,
      ServletFilterHeaderSpanDecorator.class
    );
  }

  @Test
  public void implicitStandardTags() {
    testDecorators(null,
      StandardTagsServletFilterSpanDecorator.class,
      ServletFilterHeaderSpanDecorator.class
    );
  }

  private static void testDecorators(final String spanDecoratorsArgs, final Class<?> ... expecteds) {
    final List<ServletFilterSpanDecorator> decorators = Configuration.parseSpanDecorators(spanDecoratorsArgs);
    assertEquals(expecteds.length, decorators.size());
    final List<Class<?>> list = Arrays.asList(expecteds);
    for (final ServletFilterSpanDecorator decorator : decorators)
      assertTrue(list.contains(decorator.getClass()));
  }
}