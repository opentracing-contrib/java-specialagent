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

package io.opentracing.contrib.specialagent.rule.pulsar.functions;

import static org.junit.Assert.*;

import java.util.List;

import org.apache.pulsar.functions.api.Context;
import org.apache.pulsar.functions.instance.JavaInstance;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.opentracing.contrib.specialagent.AgentRunner;
import io.opentracing.contrib.specialagent.AgentRunner.Config;
import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;
import io.opentracing.tag.Tags;

@RunWith(AgentRunner.class)
@Config(isolateClassLoader = false)
public class PulsarFunctionsTest {
  @Before
  public void before(final MockTracer tracer) {
    tracer.reset();
  }

  @Test
  public void testPulsarFunction(final MockTracer tracer) {
    try (final JavaInstance instance = new JavaInstance(null, new org.apache.pulsar.functions.api.Function<Object,Object>() {
      @Override
      public Object process(final Object o, final Context context) {
        return null;
      }
    })) {
      instance.handleMessage(null, null);
      verify(tracer);
    }
  }

  @Test
  public void testNativeFunction(final MockTracer tracer) {
    try (final JavaInstance instance = new JavaInstance(null, new java.util.function.Function<Object,Object>() {
      @Override
      public Object apply(final Object o) {
        return null;
      }
    })) {
      instance.handleMessage(null, null);
      verify(tracer);
    }
  }

  private static void verify(MockTracer tracer) {
    final List<MockSpan> spans = tracer.finishedSpans();
    assertEquals(1, spans.size());
    assertNull(tracer.activeSpan());
    for (final MockSpan span : spans)
      assertEquals(PulsarFunctionsAgentIntercept.COMPONENT_NAME, span.tags().get(Tags.COMPONENT.getKey()));
  }
}