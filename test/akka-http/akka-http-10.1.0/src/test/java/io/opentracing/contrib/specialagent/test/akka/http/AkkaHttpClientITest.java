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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.concurrent.CompletionStage;
import java.util.function.BiConsumer;

import akka.actor.ActorSystem;
import akka.http.javadsl.Http;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.stream.ActorMaterializer;
import akka.stream.Materializer;
import io.opentracing.contrib.specialagent.TestUtil;
import io.opentracing.contrib.specialagent.TestUtil.ComponentSpanCount;

public class AkkaHttpClientITest {
  public static void main(final String[] args) throws Exception {
    final ActorSystem system = ActorSystem.create();
    final Materializer materializer = ActorMaterializer.create(system);

    final Http http = getHttp(system);
    final CompletionStage<HttpResponse> stage = http.singleRequest(HttpRequest.GET("http://www.google.com"));
    stage.whenComplete(new BiConsumer<HttpResponse,Throwable>() {
      @Override
      public void accept(final HttpResponse httpResponse, final Throwable throwable) {
        TestUtil.checkActiveSpan();
        System.out.println(httpResponse.status());
      }
    }).toCompletableFuture().get().entity().getDataBytes().runForeach(param -> {}, materializer);

    stage.thenRun(system::terminate).toCompletableFuture().get();
    TestUtil.checkSpan(new ComponentSpanCount("akka-http-client", 1));
  }

  static Http getHttp(final ActorSystem system) throws IllegalAccessException, InvocationTargetException {
    // Use Reflection to call Http.get(system) because Scala Http class decompiles to java
    // class with 2 similar methods 'Http.get(system)' with difference in return type only
    for (final Method method : Http.class.getMethods())
      if (Modifier.isStatic(method.getModifiers()) && "get".equals(method.getName()) && Http.class.equals(method.getReturnType()))
        return (Http)method.invoke(null, system);

    throw new AssertionError("ERROR: failed to get Http object");
  }
}