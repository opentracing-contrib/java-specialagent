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

package io.opentracing.contrib.specialagent.rule.spring.web3;

import static org.junit.Assert.*;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.web.client.RestTemplate;

import io.opentracing.contrib.specialagent.AgentRunner;
import io.opentracing.mock.MockTracer;

@RunWith(AgentRunner.class)
public class Spring3WebTest {
  @Test
  public void test(final MockTracer tracer) {
    final RestTemplate restTemplate = new RestTemplate();

    try {
      restTemplate.getForEntity("http://localhost:12345", String.class);
    }
    catch (final Exception ignore) {

    }

    try {
      restTemplate.getForObject("http://localhost:12345", String.class);
    }
    catch (final Exception ignore) {

    }

    assertEquals(2, tracer.finishedSpans().size());
  }
}