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

package io.opentracing.contrib.specialagent.test.grizzlyhttpserver;

import static org.glassfish.grizzly.http.server.NetworkListener.*;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

import org.glassfish.grizzly.http.server.HttpHandler;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.grizzly.http.server.NetworkListener;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.grizzly.http.server.Response;

import io.opentracing.contrib.specialagent.TestUtil;

public class GrizzlyHttpServerITest {
  public static void main(final String[] args) throws IOException {
    TestUtil.initTerminalExceptionHandler();

    final HttpServer server = new HttpServer();
    final NetworkListener listener = new NetworkListener("grizzly", DEFAULT_NETWORK_HOST, 18906);
    server.addListener(listener);
    server.start();

    server.getServerConfiguration().addHttpHandler(new HttpHandler() {
      @Override
      public void service(final Request request, final Response response) {
        TestUtil.checkActiveSpan();
        response.setStatus(200);
      }
    });

    final URL url = new URL("http://localhost:18906/");
    final HttpURLConnection connection = (HttpURLConnection)url.openConnection();
    connection.setRequestMethod("GET");
    final int responseCode = connection.getResponseCode();
    connection.disconnect();

    server.shutdownNow();

    if (200 != responseCode)
      throw new AssertionError("ERROR: response: " + responseCode);

    TestUtil.checkSpan("java-grizzly-http-server", 2);
  }
}