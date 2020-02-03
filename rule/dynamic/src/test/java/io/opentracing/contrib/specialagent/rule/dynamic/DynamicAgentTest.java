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

package io.opentracing.contrib.specialagent.rule.dynamic;

import static org.junit.Assert.*;

import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.opentracing.contrib.specialagent.AgentRunner;
import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;

@RunWith(AgentRunner.class)
@AgentRunner.Config(properties = "sa.instrumentation.plugin.dynamic.rules=io.opentracing.contrib.specialagent.rule.dynamic.ExampleMethodClass#test1;io.opentracing.contrib.specialagent.rule.dynamic.ExampleMethodClass#test2(java.lang.String):java.lang.String")
public class DynamicAgentTest {
  @Before
  public void before(final MockTracer tracer) {
    tracer.reset();
  }

  @Test
  public void test1(final MockTracer tracer) {
    try {
      ExampleMethodClass.test1();
      fail("Expected Exception");
    }
    catch (final Exception ignored) {
    }

    final List<MockSpan> spans = tracer.finishedSpans();
    assertEquals(1, spans.size());
    final Map<String,Object> tags = spans.get(0).tags();
    assertNotNull(tags);
    assertEquals(Boolean.TRUE, tags.get(DynamicAgentIntercept.TAGS_KEY_ERROR));
    assertEquals("test", tags.get(DynamicAgentIntercept.TAGS_KEY_ERROR_MESSAGE));
    assertEquals(500, tags.get(DynamicAgentIntercept.TAGS_KEY_HTTP_STATUS_CODE));
  }

  @Test
  public void test2(final MockTracer tracer) {
    final ExampleMethodClass exampleMethodClass = new ExampleMethodClass();
    exampleMethodClass.test2("test");

    final List<MockSpan> spans = tracer.finishedSpans();
    assertEquals(1, spans.size());
    final Map<String,Object> tags = spans.get(0).tags();
    assertNotNull(tags);
    assertEquals(200, tags.get(DynamicAgentIntercept.TAGS_KEY_HTTP_STATUS_CODE));
  }
}