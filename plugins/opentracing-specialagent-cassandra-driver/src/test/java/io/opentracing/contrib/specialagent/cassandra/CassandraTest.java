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
package io.opentracing.contrib.specialagent.cassandra;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.datastax.driver.core.BoundStatement;
import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Session;
import io.opentracing.contrib.cassandra.TracingSession;
import io.opentracing.contrib.specialagent.AgentRunner;
import io.opentracing.mock.MockTracer;
import org.cassandraunit.utils.EmbeddedCassandraServerHelper;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AgentRunner.class)
@AgentRunner.Config(verbose = true, isolateClassLoader = false)
public class CassandraTest {

  @Before
  public void before(final MockTracer tracer) throws Exception {
    tracer.reset();
    if (isJavaSupported()) {
      EmbeddedCassandraServerHelper.startEmbeddedCassandra();
      EmbeddedCassandraServerHelper.getSession();
    }
  }

  @After
  public void after() {
    if (isJavaSupported()) {
      EmbeddedCassandraServerHelper.cleanEmbeddedCassandra();
    }
  }

  @Test
  public void test(final MockTracer tracer) {
    if (!isJavaSupported()) {
      return;
    }

    Session session = createSession();
    assertTrue(session instanceof TracingSession);

    createKeyspace(session);

    session.close();

    int size = tracer.finishedSpans().size();
    assertEquals(1, size);
  }

  private Session createSession() {
    Cluster cluster = Cluster.builder().addContactPoints("127.0.0.1").withPort(9142).build();
    return cluster.connect();
  }

  private void createKeyspace(Session session) {
    PreparedStatement prepared = session.prepare(
        "create keyspace test WITH replication = {'class':'SimpleStrategy', 'replication_factor' : 1};");
    BoundStatement bound = prepared.bind();
    session.execute(bound);
  }

  // Cassandra doesn't support yet latest JDK versions. We are still on 1.8
  private boolean isJavaSupported() {
    return System.getProperty("java.version").startsWith("1.8.");
  }
}
