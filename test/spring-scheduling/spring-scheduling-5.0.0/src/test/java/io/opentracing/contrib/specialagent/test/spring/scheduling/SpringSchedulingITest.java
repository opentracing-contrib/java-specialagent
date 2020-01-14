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

package io.opentracing.contrib.specialagent.test.spring.scheduling;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import io.opentracing.contrib.specialagent.TestUtil;

public class SpringSchedulingITest {
  public static void main(final String[] args) throws Exception {
    final CountDownLatch latch = TestUtil.initExpectedSpanLatch(2);
    try (final AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(SpringAsyncConfiguration.class)) {
      final String response = context.getBean(AsyncComponent.class).async().get(15, TimeUnit.SECONDS);
      if (!"async".equals(response))
        throw new AssertionError("ERROR: wrong async res: " + response);

      TestUtil.checkSpan("spring-scheduled", 2, latch);
    }
  }
}