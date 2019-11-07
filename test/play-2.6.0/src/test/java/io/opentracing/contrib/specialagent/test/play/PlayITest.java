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

package io.opentracing.contrib.specialagent.test.play;

import static play.mvc.Results.ok;

import io.opentracing.contrib.specialagent.TestUtil;
import java.net.HttpURLConnection;
import java.net.URL;
import play.core.PlayVersion;
import play.routing.RoutingDsl;
import play.server.Server;

public class PlayITest {
  public static void main(final String[] args) throws Exception {
    TestUtil.initTerminalExceptionHandler();
    final Server server = Server.forRouter((components) -> RoutingDsl.fromComponents(components)
        .GET("/hello/:to")
        .routeTo((request) -> {
          TestUtil.checkActiveSpan();
          return ok("Hello");
        })
        .build());

    final URL url = new URL("http://localhost:" + server.httpPort() + "/hello/world");
    final HttpURLConnection connection = (HttpURLConnection)url.openConnection();
    connection.setRequestMethod("GET");
    final int responseCode = connection.getResponseCode();
    server.stop();

    if (200 != responseCode)
      throw new AssertionError("ERROR: response: " + responseCode);

    final String playVersion = PlayVersion.current();
    if(playVersion.startsWith("2.6")) {
      TestUtil.checkSpan("play", 2); // 1 Play span + 1 Akka Actor span
    } else {
      TestUtil.checkSpan("play", 1);
    }
  }
}