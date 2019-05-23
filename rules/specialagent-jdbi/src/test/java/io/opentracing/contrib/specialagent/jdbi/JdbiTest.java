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

package io.opentracing.contrib.specialagent.jdbi;

import static org.junit.Assert.*;

import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.statement.Query;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.opentracing.contrib.specialagent.AgentRunner;
import io.opentracing.contrib.specialagent.AgentRunner.Config;
import io.opentracing.mock.MockTracer;

@RunWith(AgentRunner.class)
@Config(isolateClassLoader = false)
public class JdbiTest {
  private static void test(final Jdbi jdbi, final MockTracer tracer) {
    try (final Handle handle = jdbi.open()) {
      final Query query = handle.createQuery("SELECT COUNT(*) FROM accounts");
      handle.execute("CREATE TABLE accounts (id BIGINT AUTO_INCREMENT, PRIMARY KEY (id))");
      assertEquals("Row count", 0L, (long)query.reduceResultSet(0L, (prev, rs, ctx) -> prev + rs.getLong(1)));
      handle.execute("DROP TABLE accounts");
      assertEquals(3, tracer.finishedSpans().size());
    }
  }

  @Before
  public void before(final MockTracer tracer) {
    tracer.reset();
  }

  @Test
  public void test(final MockTracer tracer) {
    final Jdbi jdbi = Jdbi.create("jdbc:h2:mem:dbi", "sa", "");
    test(jdbi, tracer);
  }

  @Test
  public void testInstallPlugins(final MockTracer tracer) {
    final Jdbi jdbi = Jdbi.create("jdbc:h2:mem:dbi", "sa", "").installPlugins();
    test(jdbi, tracer);
  }
}