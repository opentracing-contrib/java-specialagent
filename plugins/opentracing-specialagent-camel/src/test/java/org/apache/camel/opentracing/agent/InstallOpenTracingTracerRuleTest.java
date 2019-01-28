/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.opentracing.agent;

import java.util.List;

import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.opentracing.Tracer;
import io.opentracing.contrib.specialagent.AgentRunner;
import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;
import io.opentracing.mock.MockTracer.Propagator;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;

@RunWith(AgentRunner.class)
@AgentRunner.Config(isolateClassLoader=false, verbose=true)
public class InstallOpenTracingTracerRuleTest extends CamelTestSupport {
  private static MockTracer tracer;

  @BeforeClass
  public static void beforeClass() {
    GlobalTracer.register(tracer = new MockTracer(Propagator.TEXT_MAP));
  }

  @EndpointInject(uri = "mock:result")
  protected MockEndpoint resultEndpoint;

  @Produce(uri = "direct:start")
  protected ProducerTemplate template;

  @Before
  public void init(final Tracer tracer) {
    assertTrue(InstallOpenTracingTracerRuleTest.tracer.scopeManager() == tracer.scopeManager());
    InstallOpenTracingTracerRuleTest.tracer.reset();
  }

  @Override
  protected JndiRegistry createRegistry() throws Exception {
    final JndiRegistry registry = super.createRegistry();

    // Add the mock tracer to the registry
    registry.bind("tracer", tracer);
    return registry;
  }

  @Override
  public boolean isDumpRouteCoverage() {
    return true;
  }

  @Test
  public void testSendMatchingMessage() throws InterruptedException {
    final String expectedBody = "<matched/>";

    resultEndpoint.expectedBodiesReceived(expectedBody);

    template.sendBodyAndHeader(expectedBody, "foo", "bar");

    resultEndpoint.assertIsSatisfied();

    final List<MockSpan> spans = tracer.finishedSpans();
    assertEquals(3, spans.size());
    assertEquals("mock", spans.get(0).operationName());
    assertEquals("start", spans.get(1).operationName());
    assertEquals("start", spans.get(2).operationName());
    assertEquals(Tags.SPAN_KIND_CLIENT, spans.get(0).tags().get(Tags.SPAN_KIND.getKey()));
    assertEquals(Tags.SPAN_KIND_SERVER, spans.get(1).tags().get(Tags.SPAN_KIND.getKey()));
    assertEquals(Tags.SPAN_KIND_CLIENT, spans.get(2).tags().get(Tags.SPAN_KIND.getKey()));
  }

  @Override
  protected RouteBuilder createRouteBuilder() {
    return new RouteBuilder() {
      @Override
      public void configure() {
        from("direct:start").filter(header("foo").isEqualTo("bar")).to("mock:result");
      }
    };
  }
}