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

package io.opentracing.contrib.specialagent.rule.play;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static play.mvc.Controller.ok;

import io.opentracing.contrib.specialagent.AgentRunner;
import io.opentracing.mock.MockTracer;
import io.opentracing.tag.Tags;
import java.net.HttpURLConnection;
import java.net.URL;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import play.routing.RoutingDsl;
import play.server.Server;

@RunWith(AgentRunner.class)
public class PlayTest {

  @Before
  public void before(final MockTracer tracer) {
    tracer.reset();
  }

  @Test
  public void test(final MockTracer tracer) throws Exception {
    Server server =
        Server.forRouter(
            (components) ->
                RoutingDsl.fromComponents(components)
                    .GET("/hello/:to")
                    .routeTo((request) -> {
                      assertNotNull(tracer.activeSpan());
                      return ok("Hello");
                    })
                    .build());

    final URL url = new URL("http://localhost:" + server.httpPort() + "/hello/world");
    final HttpURLConnection connection = (HttpURLConnection) url.openConnection();
    connection.setRequestMethod("GET");
    assertEquals(200, connection.getResponseCode());

    server.stop();

    assertEquals(1, tracer.finishedSpans().size());
    assertEquals(PlayAgentIntercept.COMPONENT_NAME, tracer.finishedSpans().get(0).tags().get(Tags.COMPONENT.getKey()));
  }
}