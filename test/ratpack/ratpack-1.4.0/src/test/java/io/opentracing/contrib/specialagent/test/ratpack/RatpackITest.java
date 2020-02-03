/* Copyright 2020 The OpenTracing Authors
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

package io.opentracing.contrib.specialagent.test.ratpack;

import java.net.URI;
import java.time.Duration;
import java.time.temporal.ChronoUnit;

import io.netty.buffer.PooledByteBufAllocator;
import io.opentracing.contrib.specialagent.TestUtil;
import ratpack.exec.ExecResult;
import ratpack.exec.Execution;
import ratpack.exec.Promise;
import ratpack.func.Action;
import ratpack.func.Function;
import ratpack.handling.Chain;
import ratpack.handling.Context;
import ratpack.handling.Handler;
import ratpack.http.client.HttpClient;
import ratpack.http.client.HttpClientSpec;
import ratpack.http.client.ReceivedResponse;
import ratpack.server.RatpackServer;
import ratpack.server.RatpackServerSpec;
import ratpack.server.ServerConfig;
import ratpack.test.exec.ExecHarness;

public class RatpackITest {
  public static void main(final String[] args) throws Exception {
    final RatpackServer server = RatpackServer.start(new Action<RatpackServerSpec>() {
      @Override
      public void execute(final RatpackServerSpec ratpackServerSpec) {
        ratpackServerSpec.handlers(new Action<Chain>() {
          @Override
          public void execute(final Chain chain) {
            chain.get(new Handler() {
              @Override
              public void handle(final Context context) {
                TestUtil.checkActiveSpan();
                context.render("Test");
              }
            });
          }
        });
      }
    });

    final HttpClient client = HttpClient.of(new Action<HttpClientSpec>() {
      @Override
      public void execute(final HttpClientSpec httpClientSpec) {
        httpClientSpec
          .poolSize(10)
          .maxContentLength(ServerConfig.DEFAULT_MAX_CONTENT_LENGTH)
          .readTimeout(Duration.of(60, ChronoUnit.SECONDS))
          .byteBufAllocator(PooledByteBufAllocator.DEFAULT);
      }
    });

    try (final ExecHarness harness = ExecHarness.harness()) {
      final ExecResult<ReceivedResponse> result = harness.yield(new Function<Execution,Promise<ReceivedResponse>>() {
        @Override
        public Promise<ReceivedResponse> apply(final Execution execution) {
          return client.get(URI.create("http://localhost:5050"));
        }
      });

      final int statusCode = result.getValue().getStatusCode();
      if (200 != statusCode)
        throw new AssertionError("ERROR: response: " + statusCode);
    }

    server.stop();
    TestUtil.checkSpan("netty", 2, true);
  }
}