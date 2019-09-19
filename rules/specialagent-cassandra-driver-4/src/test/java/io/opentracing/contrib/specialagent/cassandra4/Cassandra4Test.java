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

package io.opentracing.contrib.specialagent.cassandra4;

import static org.junit.Assert.assertEquals;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.BoundStatement;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.internal.core.metadata.DefaultEndPoint;
import io.opentracing.contrib.specialagent.AgentRunner;
import io.opentracing.contrib.specialagent.AgentRunner.Config;
import io.opentracing.contrib.specialagent.Level;
import io.opentracing.contrib.specialagent.Logger;
import io.opentracing.mock.MockTracer;
import java.net.InetSocketAddress;
import org.cassandraunit.utils.EmbeddedCassandraServerHelper;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AgentRunner.class)
@Config(isolateClassLoader = false)
public class Cassandra4Test {
  private static final Logger logger = Logger.getLogger(Cassandra4Test.class);

  @Before
  public void before(final MockTracer tracer) {
    tracer.reset();
  }

  @After
  public void after() {
    try {
      EmbeddedCassandraServerHelper.cleanEmbeddedCassandra();
    } catch (final Exception e) {
      logger.log(Level.WARNING, e.getMessage(), e);
    }
  }

  @Test
  public void test(final MockTracer tracer) {
    try (final CqlSession session = createSession()) {
      createKeyspace(session);
    }

    assertEquals(1, tracer.finishedSpans().size());
  }

  private static CqlSession createSession() {
    return CqlSession.builder()
        .addContactEndPoint(new DefaultEndPoint(new InetSocketAddress("127.0.0.1", 9142)))
        .withLocalDatacenter("datacenter1")
        .build();
  }

  private static void createKeyspace(final CqlSession session) {
    final PreparedStatement prepared = session.prepare("CREATE keyspace test WITH replication = {'class':'SimpleStrategy', 'replication_factor' : 1};");
    final BoundStatement bound = prepared.bind();
    session.execute(bound);
  }
}