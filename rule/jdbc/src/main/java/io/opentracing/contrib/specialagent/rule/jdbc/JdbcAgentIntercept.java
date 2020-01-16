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

package io.opentracing.contrib.specialagent.rule.jdbc;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;

import io.opentracing.contrib.jdbc.TracingDriver;
import io.opentracing.contrib.specialagent.AgentRuleUtil;
import io.opentracing.contrib.specialagent.EarlyReturnException;

public class JdbcAgentIntercept {
  public static final String IGNORE_FOR_TRACING = "sa.instrumentation.plugin.jdbc.ignoreForTracing";
  public static final String IGNORE_FOR_TRACING_SEPARATOR = "sa.instrumentation.plugin.jdbc.ignoreForTracing.separator";
  public static final String WITH_ACTIVE_SPAN_ONLY = "sa.instrumentation.plugin.jdbc.withActiveSpanOnly";
  public static final AtomicReference<Driver> tracingDriver = new AtomicReference<>();

  public static void isDriverAllowed(final Class<?> caller) {
    // FIXME: LS-11527
    if (JdbcAgentIntercept.class.getName().equals(caller.getName()) || TracingDriver.class.getName().equals(caller.getName()))
      throw new EarlyReturnException();
  }

  public static Connection connect(final String url, final Properties info) throws SQLException {
    if (AgentRuleUtil.callerEquals(2, TracingDriver.class.getName() + ".connect"))
      return null;

    if (tracingDriver.get() == null) {
      synchronized (tracingDriver) {
        if (tracingDriver.get() == null) {
          initTracingDriver();
          tracingDriver.set(TracingDriver.load());
        }
      }
    }

    return tracingDriver.get().connect(url, info);
  }

  private static void initTracingDriver() {
    TracingDriver.setInterceptorMode(true);

    final String withActiveSpanOnly = System.getProperty(WITH_ACTIVE_SPAN_ONLY);
    TracingDriver.setInterceptorProperty(withActiveSpanOnly != null && !"false".equals(withActiveSpanOnly));

    // multi-statement separated by the separator specified by a system property
    // "@@" is default separator if the system property not present
    final String separator = System.getProperty(IGNORE_FOR_TRACING_SEPARATOR, "@@");
    final String ignoreForTracing = System.getProperty(IGNORE_FOR_TRACING);
    if (ignoreForTracing != null) {
      final String[] parts = ignoreForTracing.split(separator);
      final HashSet<String> ignoreStatements = new HashSet<>();
      for (final String part : parts)
        ignoreStatements.add(part.trim());

      TracingDriver.setInterceptorProperty(ignoreStatements);
    }
  }
}