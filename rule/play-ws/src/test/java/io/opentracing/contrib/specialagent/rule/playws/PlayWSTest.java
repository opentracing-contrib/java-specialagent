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

package io.opentracing.contrib.specialagent.rule.playws;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertEquals;

import akka.actor.ActorSystem;
import akka.stream.ActorMaterializer;
import akka.stream.Materializer;
import io.opentracing.contrib.specialagent.AgentRunner;
import io.opentracing.contrib.specialagent.AgentRunner.Config;
import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;
import io.opentracing.tag.Tags;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import play.libs.ws.ahc.StandaloneAhcWSClient;
import play.shaded.ahc.org.asynchttpclient.AsyncHttpClient;
import play.shaded.ahc.org.asynchttpclient.AsyncHttpClientConfig;
import play.shaded.ahc.org.asynchttpclient.DefaultAsyncHttpClient;
import play.shaded.ahc.org.asynchttpclient.DefaultAsyncHttpClientConfig;
import scala.concurrent.Await;
import scala.concurrent.duration.Duration;

@RunWith(AgentRunner.class)
@Config(isolateClassLoader = false)
public class PlayWSTest {
  private static ActorSystem system;

  @BeforeClass
  public static void beforeClass() {
    system = ActorSystem.create();
  }

  @AfterClass
  public static void afterClass() throws Exception {
    if (system != null)
      Await.result(system.terminate(), Duration.create(15, "seconds"));
  }

  @Before
  public void before(final MockTracer tracer) {
    tracer.reset();
  }

  @Test
  public void test(final MockTracer tracer) throws Exception {
    final Materializer materializer = ActorMaterializer.create(system);

    AsyncHttpClientConfig asyncHttpClientConfig =
        new DefaultAsyncHttpClientConfig.Builder()
            .setMaxRequestRetry(0)
            .setShutdownQuietPeriod(0)
            .setShutdownTimeout(0)
            .build();

    AsyncHttpClient asyncHttpClient = new DefaultAsyncHttpClient(asyncHttpClientConfig);

    final StandaloneAhcWSClient wsClient = new StandaloneAhcWSClient(asyncHttpClient, materializer);

    try {
      wsClient.url("http://localhost:1234").get().toCompletableFuture().get(15, TimeUnit.SECONDS);
    } catch (Exception ignore) {
    }

    wsClient.close();

    await().atMost(15, TimeUnit.SECONDS).until(() -> tracer.finishedSpans().size(), equalTo(1));

    final List<MockSpan> spans = tracer.finishedSpans();
    assertEquals(1, spans.size());
    assertEquals(PlayWSAgentIntercept.COMPONENT_NAME, spans.get(0).tags().get(Tags.COMPONENT.getKey()));


  }
}