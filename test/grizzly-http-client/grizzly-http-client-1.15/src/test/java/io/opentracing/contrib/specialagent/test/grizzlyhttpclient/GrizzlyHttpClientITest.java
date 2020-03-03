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

package io.opentracing.contrib.specialagent.test.grizzlyhttpclient;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.Response;
import com.ning.http.client.SimpleAsyncHttpClient;

import io.opentracing.contrib.specialagent.TestUtil;
import io.opentracing.contrib.specialagent.TestUtil.ComponentSpanCount;

public class GrizzlyHttpClientITest {
  public static void main(final String[] args) throws InterruptedException, IOException, ExecutionException {
    try (final AsyncHttpClient client = new AsyncHttpClient()) {
      final Response response = client.prepareGet("http://www.google.com").execute().get();
      final int statusCode = response.getStatusCode();
      if (200 != statusCode)
        throw new AssertionError("ERROR: response: " + statusCode);
    }

    try (final SimpleAsyncHttpClient simpleAsyncHttpClient = new SimpleAsyncHttpClient.Builder().setUrl("http://www.google.com").build()) {
      final Response response = simpleAsyncHttpClient.get().get();
      final int statusCode = response.getStatusCode();
      if (200 != statusCode)
        throw new AssertionError("ERROR: response: " + statusCode);
    }

    TestUtil.checkSpan(new ComponentSpanCount("java-grizzly-ahc", 2));
  }
}