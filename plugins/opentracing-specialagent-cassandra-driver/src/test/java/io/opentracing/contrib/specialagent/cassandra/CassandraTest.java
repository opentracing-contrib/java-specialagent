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

import static org.junit.Assert.*;

import java.util.logging.Logger;

import org.cassandraunit.utils.EmbeddedCassandraServerHelper;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.datastax.driver.core.BoundStatement;
import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Session;

import io.opentracing.contrib.cassandra.TracingSession;
import io.opentracing.contrib.specialagent.AgentRunner;
import io.opentracing.contrib.specialagent.Manager.Event;
import io.opentracing.mock.MockTracer;

@RunWith(AgentRunner.class)
@AgentRunner.Config(events=Event.ERROR, isolateClassLoader=false)
public class CassandraTest {
  private static final Logger logger = Logger.getLogger(CassandraTest.class.getName());

  // Cassandra doesn't yet support the latest JDK versions. We are still on 1.8
  private static final boolean isJdkSupported = System.getProperty("java.version").startsWith("1.8.");

  @Before
  public void before(final MockTracer tracer) throws Exception {
    tracer.reset();
    if (isJdkSupported) {
      EmbeddedCassandraServerHelper.startEmbeddedCassandra();
      EmbeddedCassandraServerHelper.getSession();
    }
  }

  @After
  public void after() {
    if (isJdkSupported) {
      EmbeddedCassandraServerHelper.cleanEmbeddedCassandra();
    }
  }

  @Test
  public void test(final MockTracer tracer) {
    if (!isJdkSupported) {
      logger.warning("jdk" + System.getProperty("java.version") + " is not supported by Cassandra.");
      return;
    }

    try (final Session session = createSession()) {
      assertTrue(session.getClass().getName(), session instanceof TracingSession);
      createKeyspace(session);
      session.close();
    }

    final int size = tracer.finishedSpans().size();
    assertEquals(1, size);
  }

  private static Session createSession() {
    final Cluster cluster = Cluster.builder().addContactPoints("127.0.0.1").withPort(9142).build();
    return cluster.connect();
  }

  private static void createKeyspace(Session session) {
    final PreparedStatement prepared = session.prepare("CREATE keyspace test WITH replication = {'class':'SimpleStrategy', 'replication_factor' : 1};");
    final BoundStatement bound = prepared.bind();
    session.execute(bound);
  }
}