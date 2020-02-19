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

package io.opentracing.contrib.specialagent.test.jaxrs;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import io.opentracing.contrib.specialagent.TestUtil;
import io.opentracing.contrib.specialagent.TestUtil.ComponentSpanCount;

public class JaxRsClientITest {
  public static void main(final String[] args) throws Exception {
    final Client client = ClientBuilder.newClient();
    final Response response = client.target("http://www.google.com").request(MediaType.TEXT_PLAIN).get();
    final int statusCode = response.getStatus();

    if (200 != statusCode)
      throw new AssertionError("ERROR: response: " + statusCode);

    TestUtil.checkSpan(new ComponentSpanCount("jaxrs", 1));
  }
}