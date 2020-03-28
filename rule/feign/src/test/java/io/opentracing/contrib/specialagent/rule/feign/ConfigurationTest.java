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

package io.opentracing.contrib.specialagent.rule.feign;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import feign.opentracing.FeignSpanDecorator;

public class ConfigurationTest {
  @Test
  public void testExplicitSpanDecorators() {
    testDecorators(FeignSpanDecorator.StandardTags.class.getName() + "," + MockSpanDecorator.class.getName(), FeignSpanDecorator.StandardTags.class, MockSpanDecorator.class);
  }

  @Test
  public void testImplicitSpanDecorators() {
    testDecorators(null, FeignSpanDecorator.StandardTags.class);
  }

  private static void testDecorators(final String spanDecoratorsArgs, final Class<?> ... expecteds) {
    final List<FeignSpanDecorator> decorators = Configuration.parseSpanDecorators(spanDecoratorsArgs);
    assertEquals(expecteds.length, decorators.size());
    final List<Class<?>> list = Arrays.asList(expecteds);
    for (final FeignSpanDecorator decorator : decorators)
      assertTrue(list.contains(decorator.getClass()));
  }
}