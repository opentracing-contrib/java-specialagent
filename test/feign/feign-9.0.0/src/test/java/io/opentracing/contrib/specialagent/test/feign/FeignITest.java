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

package io.opentracing.contrib.specialagent.test.feign;

import java.util.concurrent.TimeUnit;

import feign.Feign;
import feign.Headers;
import feign.RequestLine;
import feign.Retryer;
import feign.Target;
import io.opentracing.contrib.specialagent.TestUtil;
import io.opentracing.contrib.specialagent.TestUtil.ComponentSpanCount;

public class FeignITest {
  public static void main(final String[] args) {
    final Feign feign = Feign.builder().retryer(new Retryer.Default(100, TimeUnit.SECONDS.toMillis(1), 2)).build();
    feign.newInstance(new Target.HardCodedTarget<>(StringEntityRequest.class, "http://www.google.com")).get();
    TestUtil.checkSpan(new ComponentSpanCount("feign", 1));
  }

  private interface StringEntityRequest {
    @RequestLine("GET")
    @Headers("Content-Type: application/json")
    String get();
  }
}