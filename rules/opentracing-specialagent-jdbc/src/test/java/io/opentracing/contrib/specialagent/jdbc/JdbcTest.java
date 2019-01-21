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
import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;

@RunWith(AgentRunner.class)
@AgentRunner.Config(verbose=true)
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