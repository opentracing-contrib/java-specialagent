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

package io.opentracing.contrib.specialagent.rule.couchbase;

import static org.junit.Assert.assertTrue;

import com.couchbase.client.java.env.CouchbaseEnvironment;
import com.couchbase.client.java.env.DefaultCouchbaseEnvironment;
import io.opentracing.contrib.specialagent.AgentRunner;
import io.opentracing.mock.MockTracer;
import io.opentracing.util.GlobalTracer;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AgentRunner.class)
public class CouchbaseClientTest {
  @Before
  public void before(final MockTracer tracer) {
    tracer.reset();
  }

  @Test
  public void test(final MockTracer tracer) {
    // There is no embedded couchbase server to test real scenario
    CouchbaseEnvironment env = DefaultCouchbaseEnvironment.builder().build();
    assertTrue(env.tracer() instanceof GlobalTracer);
  }

}