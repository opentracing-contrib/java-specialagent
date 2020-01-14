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

package io.opentracing.contrib.specialagent.test.akka.http;

import static io.opentracing.contrib.specialagent.test.akka.http.AkkaHttpClientITest.*;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import akka.actor.ActorSystem;
import akka.http.javadsl.ConnectHttp;
import akka.http.javadsl.Http;
import akka.http.javadsl.ServerBinding;
import akka.http.javadsl.model.HttpResponse;
import akka.stream.ActorMaterializer;
import akka.stream.Materializer;
import akka.util.ByteString;
import io.opentracing.contrib.specialagent.TestUtil;
import scala.concurrent.Await;
import scala.concurrent.duration.Duration;

public class AkkaHttpServerITest {
  public static void main(final String[] args) throws Exception {
    final ActorSystem system = ActorSystem.create();
    final Materializer materializer = ActorMaterializer.create(system);
    final Http http = getHttp(system);

    testSync(http, materializer);
    testAsync(http, materializer);

    Await.result(system.terminate(), Duration.create(15, TimeUnit.SECONDS));
  }

  private static void testSync(final Http http, final Materializer materializer) throws Exception {
    final CompletionStage<ServerBinding> binding = http.bindAndHandleSync(request -> HttpResponse.create().withEntity(ByteString.fromString("OK")), ConnectHttp.toHost("localhost", 8081), materializer);
    final ServerBinding serverBinding = binding.toCompletableFuture().get();
    test(serverBinding);
  }

  private static void testAsync(final Http http, final Materializer materializer) throws Exception {
    final CompletionStage<ServerBinding> binding = http.bindAndHandleAsync(request -> CompletableFuture.supplyAsync(() -> HttpResponse.create().withEntity(ByteString.fromString("OK"))), ConnectHttp.toHost("localhost", 8082), materializer);
    final ServerBinding serverBinding = binding.toCompletableFuture().get();
    test(serverBinding);
  }

  private static void test(final ServerBinding serverBinding) throws Exception {
    final URL url = new URL("http://localhost:" + serverBinding.localAddress().getPort());
    final HttpURLConnection connection = (HttpURLConnection)url.openConnection();
    connection.setRequestMethod("GET");
    final int responseCode = connection.getResponseCode();
    serverBinding.unbind().toCompletableFuture().get();

    if (200 != responseCode)
      throw new AssertionError("ERROR: response: " + responseCode);

    TestUtil.checkSpan("akka-http-server", 2);
  }
}