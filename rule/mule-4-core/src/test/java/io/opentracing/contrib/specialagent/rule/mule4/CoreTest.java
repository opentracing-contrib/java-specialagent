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

package io.opentracing.contrib.specialagent.rule.mule4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mule.tck.junit4.AbstractMuleTestCase;

import io.opentracing.contrib.specialagent.AgentRunner;
import io.opentracing.mock.MockTracer;

@RunWith(AgentRunner.class)
public class CoreTest extends AbstractMuleTestCase {
  @Before
  public void before(final MockTracer tracer) throws Exception {
    // clear traces
    tracer.reset();

  }

  @After
  public void after() throws Exception {
  }

  @Test
  public void test(final MockTracer tracer) throws Exception {
    // TODO: 1/10/20 Figure out how to make MuleArtifactFunctionalTestCase work
    // with AgentRunner
    // until then the mule-4.x/container-itest will be our only coverage
  }
}