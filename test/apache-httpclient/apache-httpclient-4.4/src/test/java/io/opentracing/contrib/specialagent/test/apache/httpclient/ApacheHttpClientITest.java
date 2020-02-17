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

package io.opentracing.contrib.specialagent.test.apache.httpclient;

import io.opentracing.contrib.specialagent.TestUtil.ComponentSpanCount;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;

import io.opentracing.contrib.specialagent.TestUtil;

public class ApacheHttpClientITest {
  public static void main(final String[] args) throws Exception {
    final HttpClient client = HttpClientBuilder.create().build();
    final HttpResponse response = client.execute(new HttpGet("http://www.google.com"));
    final int statusCode = response.getStatusLine().getStatusCode();

    if (200 != statusCode)
      throw new AssertionError("ERROR: response: " + statusCode);

    TestUtil.checkSpan(new ComponentSpanCount("java-httpclient", 1));
  }
}