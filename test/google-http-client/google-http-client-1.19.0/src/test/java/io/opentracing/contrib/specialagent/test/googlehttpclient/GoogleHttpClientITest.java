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

package io.opentracing.contrib.specialagent.test.googlehttpclient;

import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.javanet.NetHttpTransport;

import io.opentracing.contrib.specialagent.TestUtil;
import io.opentracing.contrib.specialagent.TestUtil.ComponentSpanCount;

public class GoogleHttpClientITest {
  public static void main(final String[] args) throws Exception {
    final HttpRequestFactory requestFactory = new NetHttpTransport().createRequestFactory();
    final HttpRequest request = requestFactory.buildGetRequest(new GenericUrl("https://www.google.com"));
    final int statusCode = request.execute().getStatusCode();
    if (200 != statusCode)
      throw new AssertionError("ERROR: response: " + statusCode);

    TestUtil.checkSpan(new ComponentSpanCount("google-http-client", 1));
  }
}