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

package io.opentracing.contrib.specialagent.test.reactor;

import java.util.concurrent.atomic.AtomicReference;

import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.contrib.specialagent.TestUtil;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import reactor.core.publisher.Mono;

public class ReactorITest {
  public static void main(final String[] args) {
    final Span initSpan = GlobalTracer.get().buildSpan("foo").withTag(Tags.COMPONENT, "parent-reactor").start();
    final AtomicReference<String> spanInSubscriberContext = new AtomicReference<>();
    try (final Scope scope = GlobalTracer.get().scopeManager().activate(initSpan)) {
      Mono.subscriberContext().map(context -> (context.get(Span.class)).context().toSpanId()).doOnNext(spanInSubscriberContext::set).block();
    }
    finally {
      initSpan.finish();
    }

    if (!spanInSubscriberContext.get().equals(initSpan.context().toSpanId()))
      throw new AssertionError("ERROR: not equal span id");

    TestUtil.checkSpan("parent-reactor", 1);
  }
}