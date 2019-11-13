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

package io.opentracing.contrib.specialagent.test.asynchttpclient;

import java.util.concurrent.TimeUnit;

import org.asynchttpclient.AsyncCompletionHandler;
import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.DefaultAsyncHttpClient;
import org.asynchttpclient.Request;
import org.asynchttpclient.RequestBuilder;
import org.asynchttpclient.Response;
import org.asynchttpclient.util.HttpConstants;

import io.opentracing.contrib.specialagent.TestUtil;

public class AsyncHttpClientITest {
  public static void main(final String[] args) throws Exception {
    TestUtil.initTerminalExceptionHandler();
    try (final AsyncHttpClient client = new DefaultAsyncHttpClient()) {
      final Request request = new RequestBuilder(HttpConstants.Methods.GET).setUrl("http://www.google.com").build();
      final int statusCode = client.executeRequest(request, new AsyncCompletionHandler<Response>() {
        @Override
        public Response onCompleted(final Response response) {
          TestUtil.checkActiveSpan();
          return response;
        }
      }).get(10, TimeUnit.SECONDS).getStatusCode();

      if (200 != statusCode)
        throw new AssertionError("ERROR: response: " + statusCode);

      TestUtil.checkSpan("java-asynchttpclient", 1);
    }
  }
}