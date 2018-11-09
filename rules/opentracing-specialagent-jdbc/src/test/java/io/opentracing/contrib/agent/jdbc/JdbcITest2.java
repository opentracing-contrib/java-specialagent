package io.opentracing.contrib.agent.jdbc;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import org.junit.Test;

public class JdbcITest2 {
  @Test
  public void test() throws Exception {
    Class.forName("org.h2.Driver");
    try (
      final Connection connection = DriverManager.getConnection("jdbc:h2:mem:jdbc");
    ) {
      final Statement statement = connection.createStatement();
      statement.executeUpdate("CREATE TABLE employer (id INTEGER)");
      connection.close();
    }
  }
}