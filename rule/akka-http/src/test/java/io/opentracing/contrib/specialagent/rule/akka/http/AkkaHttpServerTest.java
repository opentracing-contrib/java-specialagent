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

package io.opentracing.contrib.specialagent.rule.akka.http;

import static io.opentracing.contrib.specialagent.rule.akka.http.AkkaHttpClientTest.*;
import static org.awaitility.Awaitility.*;
import static org.hamcrest.core.IsEqual.*;
import static org.junit.Assert.*;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import akka.actor.ActorSystem;
import akka.http.javadsl.ConnectHttp;
import akka.http.javadsl.Http;
import akka.http.javadsl.ServerBinding;
import akka.http.javadsl.model.HttpResponse;
import akka.stream.ActorMaterializer;
import akka.stream.Materializer;
import akka.util.ByteString;
import io.opentracing.contrib.specialagent.AgentRunner;
import io.opentracing.contrib.specialagent.AgentRunner.Config;
import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;
import io.opentracing.tag.Tags;
import scala.concurrent.Await;
import scala.concurrent.duration.Duration;

@RunWith(AgentRunner.class)
@Config(isolateClassLoader = false)
public class AkkaHttpServerTest {
  private static ActorSystem system;
  private static Materializer materializer;
  private static Http http;

  @BeforeClass
  public static void beforeClass() throws IllegalAccessException, InvocationTargetException {
    system = ActorSystem.create();
    materializer = ActorMaterializer.create(system);
    http = getHttp(system);
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
  public void testSync(final MockTracer tracer) throws ExecutionException, InterruptedException, IOException {
    final CompletionStage<ServerBinding> binding = http.bindAndHandleSync(request -> HttpResponse.create().withEntity(ByteString.fromString("OK")), ConnectHttp.toHost("localhost", 8081), materializer);
    final ServerBinding serverBinding = binding.toCompletableFuture().get();
    test(tracer, serverBinding);
  }

  @Test
  public void testAsync(final MockTracer tracer) throws ExecutionException, InterruptedException, IOException {
    final CompletionStage<ServerBinding> binding = http.bindAndHandleAsync(request -> CompletableFuture.supplyAsync(() -> HttpResponse.create().withEntity(ByteString.fromString("OK"))), ConnectHttp.toHost("localhost", 8082), materializer);
    final ServerBinding serverBinding = binding.toCompletableFuture().get();
    test(tracer, serverBinding);
  }

  private static void test(final MockTracer tracer, final ServerBinding serverBinding) throws ExecutionException, InterruptedException, IOException {
    final URL url = new URL("http://localhost:" + serverBinding.localAddress().getPort());
    final HttpURLConnection connection = (HttpURLConnection)url.openConnection();
    connection.setRequestMethod("GET");
    assertEquals(200, connection.getResponseCode());
    serverBinding.unbind().toCompletableFuture().get();

    await().atMost(15, TimeUnit.SECONDS).until(() -> tracer.finishedSpans().size(), equalTo(1));

    final List<MockSpan> spans = tracer.finishedSpans();
    assertEquals(1, spans.size());
    assertEquals(AkkaAgentIntercept.COMPONENT_NAME_SERVER, spans.get(0).tags().get(Tags.COMPONENT.getKey()));
  }
}