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

package io.opentracing.contrib.specialagent.test.cassandra.driver;

import java.io.File;
import java.net.InetSocketAddress;

import org.cassandraunit.utils.EmbeddedCassandraServerHelper;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.internal.core.metadata.DefaultEndPoint;

import io.opentracing.contrib.specialagent.TestUtil;

public class Cassandra4ITest {
  public static void main(final String[] args) throws Exception {
    if (!System.getProperty("java.version").startsWith("1.8.")) {
      System.err.println("Cassandra only works with jdk1.8.");
      return;
    }

    System.getProperties().setProperty("java.library.path", new File("src/test/resources/libs").getAbsolutePath());

    final File triggers = new File("target/triggers");
    triggers.mkdirs();
    System.setProperty("cassandra.triggers_dir", triggers.getAbsolutePath());

    EmbeddedCassandraServerHelper.startEmbeddedCassandra(60_000); // 1 minute
    // EmbeddedCassandraServerHelper.getSession();

    try (final CqlSession session = CqlSession.builder()
      .addContactEndPoint(new DefaultEndPoint(new InetSocketAddress("127.0.0.1", 9142)))
      .withLocalDatacenter("datacenter1")
      .build()) {
      final ResultSet resultSet = session.execute("SELECT * FROM system.compaction_history");
      System.out.println("Rows: " + resultSet.all().size());
    }

    TestUtil.checkSpan("java-cassandra", 1);

    EmbeddedCassandraServerHelper.cleanEmbeddedCassandra();
    System.exit(0);
  }
}