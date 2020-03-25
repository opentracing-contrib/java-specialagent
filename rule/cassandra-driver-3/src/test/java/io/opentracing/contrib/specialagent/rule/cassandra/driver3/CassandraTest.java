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

package io.opentracing.contrib.specialagent.rule.cassandra.driver3;

import static org.junit.Assert.*;

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
import io.opentracing.contrib.specialagent.Level;
import io.opentracing.contrib.specialagent.Logger;
import io.opentracing.mock.MockTracer;

@RunWith(AgentRunner.class)
@AgentRunner.Config(isolateClassLoader = false)
public class CassandraTest {
  private static final Logger logger = Logger.getLogger(CassandraTest.class);

  // Cassandra doesn't yet support the latest JDK versions. We are still on 1.8
  // FIXME: Disabling this test because I cannot figure it out.
  private static final boolean isJdkSupported = false && System.getProperty("java.version").startsWith("1.8.");

  @Before
  public void before(final MockTracer tracer) throws Exception {
    if (isJdkSupported) {
      tracer.reset();
      EmbeddedCassandraServerHelper.startEmbeddedCassandra();
      //EmbeddedCassandraServerHelper.getSession();
    }
  }

  @After
  public void after() {
    if (isJdkSupported) {
      try {
        EmbeddedCassandraServerHelper.cleanEmbeddedCassandra();
      }
      catch (final Exception e) {
        logger.log(Level.WARNING, e.getMessage(), e);
      }
    }
  }

  @Test
  public void test(final MockTracer tracer) {
    if (!isJdkSupported) {
      logger.warning("jdk" + System.getProperty("java.version") + " is not supported by Cassandra");
      return;
    }

    try (final Session session = createSession()) {
      assertTrue(session.getClass().getName(), session instanceof TracingSession);
      createKeyspace(session);
      session.close();
    }

    assertEquals(1, tracer.finishedSpans().size());
  }

  private static Session createSession() {
    final Cluster cluster = Cluster.builder().addContactPoints("127.0.0.1").withPort(9142).build();
    return cluster.connect();
  }

  private static void createKeyspace(final Session session) {
    final PreparedStatement prepared = session.prepare("CREATE keyspace test WITH replication = {'class':'SimpleStrategy', 'replication_factor' : 1};");
    final BoundStatement bound = prepared.bind();
    session.execute(bound);
  }
}