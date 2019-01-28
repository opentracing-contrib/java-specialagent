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

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Enumeration;
import java.util.Properties;

import io.opentracing.contrib.jdbc.TracingDriver;

public class JdbcAgentIntercept {
  public static final ThreadLocal<Boolean> mutex = new ThreadLocal<Boolean>() {
    @Override
    protected Boolean initialValue() {
      return Boolean.FALSE;
    }
  };

  public static TracingDriver tracingDriver;

  public static Connection enter(final String url, final Properties info) throws SQLException {
    if (mutex.get())
      return null;

    mutex.set(true);
    if (tracingDriver == null) {
      try {
        Class.forName(TracingDriver.class.getName());
      }
      catch (final ClassNotFoundException e) {
        throw new IllegalStateException("TracingDriver initialization failed", e);
      }

      final Enumeration<Driver> drivers = DriverManager.getDrivers();
      while (drivers.hasMoreElements()) {
        final Driver driver = drivers.nextElement();
        if (driver instanceof TracingDriver) {
          tracingDriver = (TracingDriver)driver;
          break;
        }
      }

      if (tracingDriver == null)
        throw new IllegalStateException("TracingDriver initialization failed");
    }

    return tracingDriver.connect(!url.startsWith("jdbc:tracing:") ? "jdbc:tracing:" + url.substring(5) : url, info);
  }
}