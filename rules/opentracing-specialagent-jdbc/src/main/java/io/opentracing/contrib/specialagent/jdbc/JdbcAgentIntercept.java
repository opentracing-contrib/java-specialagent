package io.opentracing.contrib.specialagent.jdbc;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collections;

import io.opentracing.contrib.jdbc.TracingConnection;
import io.opentracing.util.GlobalTracer;

public class JdbcAgentIntercept {
  public static Connection exit(final Object returned) throws SQLException {
    if (returned == null)
      return null;

    final Connection connection = (Connection)returned;
    return new TracingConnection(connection,
      connection.getMetaData().getURL().split(":")[1],
      connection.getMetaData().getUserName(),
      true,
      Collections.<String>emptySet(),
      GlobalTracer.get());
  }
}