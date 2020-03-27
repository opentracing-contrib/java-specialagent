/* Copyright 2019 The OpenTracing Authors
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
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import feign.Client;
import feign.Feign;
import feign.Headers;
import feign.RequestLine;
import feign.Retryer;
import feign.Target;
import feign.okhttp.OkHttpClient;
import io.opentracing.Scope;
import io.opentracing.contrib.specialagent.AgentRunner;
import io.opentracing.contrib.specialagent.rule.feign.Configuration;
import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;

@RunWith(AgentRunner.class)
@AgentRunner.Config(isolateClassLoader = false)
public class FeignTest {

  @BeforeClass
  public static void beforeClass() {
    System.setProperty(Configuration.SPAN_DECORATORS, "feign.opentracing.FeignSpanDecorator$StandardTags,io.opentracing.contrib.specialagent.rule.feign.MockSpanDecorator");
  }

  @Before
  public void before(final MockTracer tracer) {
    tracer.reset();
  }

  @Test
  public void testImplicitClient(final MockTracer tracer) {
    final Feign feign = getImplicitClient();
    test(feign, tracer);
  }

  @Test
  public void testImplicitClientWithBuilderConstructor(final MockTracer tracer) {
    final Feign feign = getImplicitClientWithBuilderConstructor();
    test(feign, tracer);
  }

  @Test
  public void testExplicitClientWithBuilderConstructor(final MockTracer tracer) {
    final Feign feign = getExplicitClientWithBuilderConstructor();
    test(feign, tracer);
  }

  @Test
  public void testExplicitClient(final MockTracer tracer) {
    final Feign feign = getExplicitClient();
    test(feign, tracer);
  }

  @Test
  public void testWithParent(final MockTracer tracer) {
    final Feign feign = getImplicitClient();
    final MockSpan parent = tracer.buildSpan("parent").start();
    try (final Scope ignore = tracer.activateSpan(parent)) {
      test(feign, tracer);
    }

    for (final MockSpan span : tracer.finishedSpans()) {
      assertEquals(parent.context().traceId(), span.context().traceId());
    }
  }

  private static void test(final Feign feign, final MockTracer tracer) {
    final StringEntityRequest entity = feign.<StringEntityRequest>newInstance(new Target.HardCodedTarget<>(StringEntityRequest.class, "http://localhost:12345"));
    try {
      final String res = entity.get();
      System.out.println(res);
    }
    catch (final Exception ignore) {
    }

    assertEquals(2, tracer.finishedSpans().size());
    for (final MockSpan span : tracer.finishedSpans())
      assertEquals(MockSpanDecorator.MOCK_TAG_VALUE, span.tags().get(MockSpanDecorator.MOCK_TAG_KEY));
  }

  private static Feign getImplicitClient() {
    return Feign.builder().retryer(new Retryer.Default(100, TimeUnit.SECONDS.toMillis(1), 2)).build();
  }

  private static Feign getExplicitClient() {
    return Feign.builder().client((new Client.Default(null, null))).retryer(new Retryer.Default(100, TimeUnit.SECONDS.toMillis(1), 2)).build();
  }

  private static Feign getImplicitClientWithBuilderConstructor() {
    return new Feign.Builder().retryer(new Retryer.Default(100, TimeUnit.SECONDS.toMillis(1), 2)).build();
  }

  private static Feign getExplicitClientWithBuilderConstructor() {
    return new Feign.Builder().client((new OkHttpClient())).retryer(new Retryer.Default(100, TimeUnit.SECONDS.toMillis(1), 2)).build();
  }

  private interface StringEntityRequest {
    @RequestLine("GET")
    @Headers("Content-Type: application/json")
    String get();
  }
}