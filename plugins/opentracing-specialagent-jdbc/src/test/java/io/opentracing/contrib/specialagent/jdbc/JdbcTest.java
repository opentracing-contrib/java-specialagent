/* Copyright 2018 The OpenTracing Authors
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

package io.opentracing.contrib.specialagent.jdbc;

import static org.junit.Assert.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;

import io.opentracing.Scope;
import io.opentracing.contrib.specialagent.AgentRunner;
import io.opentracing.contrib.specialagent.Manager.Event;
import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;

@RunWith(AgentRunner.class)
@AgentRunner.Config(events=Event.ERROR)
public class JdbcTest {
  @Test
  public void test(final MockTracer tracer) throws Exception {
    Class.forName("org.h2.Driver");
    try (
      final Scope ignored = tracer.buildSpan("jdbc-test").startActive(true);
      final Connection connection = DriverManager.getConnection("jdbc:h2:mem:jdbc");
    ) {
      final Statement statement = connection.createStatement();
      statement.executeUpdate("CREATE TABLE employer (id INTEGER)");
      connection.close();

      final List<MockSpan> spans = tracer.finishedSpans();
      assertEquals(1, spans.size());
    }
  }
}