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

package io.opentracing.contrib.specialagent.test.neo4j.driver;

import io.opentracing.contrib.specialagent.TestUtil.ComponentSpanCount;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Query;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.harness.ServerControls;
import org.neo4j.harness.TestServerBuilders;

import io.opentracing.contrib.specialagent.TestUtil;

public class Neo4jDriverITest {
  public static void main(final String[] args) {
    try (final ServerControls server = TestServerBuilders.newInProcessBuilder().newServer()) {
      try (Driver driver = GraphDatabase.driver(server.boltURI().toString())) {
        try (final Session session = driver.session()) {
          final Result result = session.run(new Query("CREATE (n:Person) RETURN n"));
          System.out.println(result.single());
        }

        try (final Session session = driver.session()) {
          final Result result = session.run("CREATE (n:Person) RETURN n");
          System.out.println(result.single());
        }
      }
    }

    TestUtil.checkSpan(new ComponentSpanCount("java-neo4j", 2));
  }
}