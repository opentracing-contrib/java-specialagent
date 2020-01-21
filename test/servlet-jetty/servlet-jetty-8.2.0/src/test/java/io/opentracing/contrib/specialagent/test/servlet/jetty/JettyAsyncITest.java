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

package io.opentracing.contrib.specialagent.test.servlet.jetty;

import java.net.HttpURLConnection;
import java.net.URL;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

import io.opentracing.contrib.specialagent.AssembleUtil;
import io.opentracing.contrib.specialagent.TestUtil;

public class JettyAsyncITest {
  public static void main(final String[] args) throws Exception {
    final Server server = new Server(8080);
    final ServletContextHandler context = new ServletContextHandler();
    context.setContextPath("/");
    final ServletHolder asyncHolder = context.addServlet(HelloAsyncServlet.class, "/async");
    asyncHolder.setAsyncSupported(true);
    server.setHandler(context);

    try {
      server.start();
      final URL url = new URL("http://localhost:8080/async");
      final HttpURLConnection connection = (HttpURLConnection)url.openConnection();
      connection.setRequestMethod("GET");
      final int responseCode = connection.getResponseCode();
      System.out.println("Response Code : " + responseCode);
      try {
        System.out.println("Output: " + AssembleUtil.readBytes(connection.getInputStream()));
      }
      catch (final Throwable ignore) {
      }

      try {
        System.out.println("Output: " + AssembleUtil.readBytes(connection.getInputStream()));
      }
      catch (final Throwable ignore) {
      }
    }
    finally {
      server.stop();
      server.join();
    }

    TestUtil.checkSpan("java-web-servlet", 2);
  }
}