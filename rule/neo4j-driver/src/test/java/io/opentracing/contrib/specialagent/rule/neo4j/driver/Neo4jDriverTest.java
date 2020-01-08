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

package io.opentracing.contrib.specialagent.rule.neo4j.driver;

import static org.junit.Assert.assertEquals;

import io.opentracing.contrib.specialagent.AgentRunner;
import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;
import io.opentracing.tag.Tags;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Query;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.harness.junit.Neo4jRule;

@RunWith(AgentRunner.class)
public class Neo4jDriverTest {

  @Rule
  public Neo4jRule neoServer = new Neo4jRule();
  private Driver driver;


  @Before
  public void before(final MockTracer tracer) {
    tracer.reset();
    driver = GraphDatabase.driver(neoServer.boltURI().toString());
  }

  @After
  public void after() {
    if(driver != null)
      driver.close();
  }

  @Test
  public void test(final MockTracer tracer) {
    try (Session session = driver.session()) {
      Result result = session.run(new Query("CREATE (n:Person) RETURN n"));
      System.out.println(result.single());
    }

    try (Session session = driver.session()) {
      Result result = session.run("CREATE (n:Person) RETURN n");
      System.out.println(result.single());
    }

    List<MockSpan> spans = tracer.finishedSpans();
    assertEquals(2, spans.size());

    for (MockSpan span : spans) {
      assertEquals("java-neo4j", span.tags().get(Tags.COMPONENT.getKey()));
    }
  }

}