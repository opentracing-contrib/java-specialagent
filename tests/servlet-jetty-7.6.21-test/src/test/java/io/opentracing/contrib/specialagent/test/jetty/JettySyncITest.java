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

package io.opentracing.contrib.specialagent.test.jetty;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.webapp.WebAppContext;

import io.opentracing.contrib.specialagent.AssembleUtil;
import io.opentracing.contrib.specialagent.TestUtil;

public class JettySyncITest {
  public static void main(final String[] args) throws Exception {
    final Server server = new Server(8080);
    final WebAppContext context = new WebAppContext();
    context.setServer(server);
    context.setContextPath("/");
    context.setWar(installWebApp().getPath());
    server.setHandler(context);

    try {
      server.start();
      final URL url = new URL("http://localhost:8080/sync");
      final HttpURLConnection connection = (HttpURLConnection)url.openConnection();
      connection.setRequestMethod("GET");
      final int responseCode = connection.getResponseCode();
      System.out.println("Response Code : " + responseCode);
      System.out.println("Output: " + AssembleUtil.readBytes(connection.getInputStream()));
    }
    finally {
      server.stop();
      server.join();
    }

    TestUtil.checkSpan("java-web-servlet", 1);
  }

  static File installWebApp() throws IOException {
    final InputStream in = ClassLoader.getSystemClassLoader().getResourceAsStream("web.xml");
    final File webapp = new File("target/webapp/WEB-INF/web.xml");
    webapp.getParentFile().mkdirs();
    Files.copy(in, webapp.toPath(), StandardCopyOption.REPLACE_EXISTING);
    return webapp.getParentFile().getParentFile();
  }
}