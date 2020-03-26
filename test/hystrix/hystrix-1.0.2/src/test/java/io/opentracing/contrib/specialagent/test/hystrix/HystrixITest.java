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

package io.opentracing.contrib.specialagent.test.hystrix;

import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixCommandGroupKey;

import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.contrib.specialagent.TestUtil;
import io.opentracing.contrib.specialagent.TestUtil.ComponentSpanCount;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;

public class HystrixITest {
  public static void main(final String[] args) {
    final Span parent = GlobalTracer.get().buildSpan("parent").withTag(Tags.COMPONENT, "hystrix").start();
    try (final Scope ignored = GlobalTracer.get().activateSpan(parent)) {
      final String res = new HystrixTestCommand().execute();
      if (!res.equalsIgnoreCase("test"))
        throw new AssertionError("ERROR: failed hystrix res: " + res);
    }
    finally {
      parent.finish();
    }

    TestUtil.checkSpan(new ComponentSpanCount("hystrix", 1));
    System.exit(0); // There is no way to shutdown all Hystix thread pools
  }

  private static class HystrixTestCommand extends HystrixCommand<String> {
    private HystrixTestCommand() {
      super(HystrixCommandGroupKey.Factory.asKey("ExampleGroup"));
    }

    @Override
    protected String run() {
      TestUtil.checkActiveSpan();
      return "test";
    }
  }
}