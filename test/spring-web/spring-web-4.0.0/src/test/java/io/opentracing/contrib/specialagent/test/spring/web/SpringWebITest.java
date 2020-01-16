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

package io.opentracing.contrib.specialagent.test.spring.web;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.springframework.http.ResponseEntity;
import org.springframework.util.concurrent.ListenableFutureCallback;
import org.springframework.web.client.AsyncRestTemplate;
import org.springframework.web.client.RestTemplate;

import io.opentracing.contrib.specialagent.TestUtil;

public class SpringWebITest {
  public static void main(final String[] args) throws Exception {
    makeAsyncCall();

    final RestTemplate restTemplate = new RestTemplate();
    restTemplate.getForObject("http://www.google.com", String.class);

    TestUtil.checkSpan("java-spring-rest-template", 3);
  }

  private static boolean makeAsyncCall() throws Exception {
    final AsyncRestTemplate asyncRestTemplate = new AsyncRestTemplate();
    final int status = asyncRestTemplate.getForEntity("http://www.google.com", String.class).get(10, TimeUnit.SECONDS).getStatusCode().value();

    if (200 != status)
      throw new AssertionError("ERROR: response: " + status);

    final CountDownLatch latch = new CountDownLatch(1);

    asyncRestTemplate.getForEntity("http://www.google.com", String.class).addCallback(new ListenableFutureCallback<ResponseEntity<String>>() {
      @Override
      public void onSuccess(final ResponseEntity<String> result) {
        TestUtil.checkActiveSpan();
        latch.countDown();
      }

      @Override
      public void onFailure(final Throwable t) {
        TestUtil.checkActiveSpan();
        latch.countDown();
      }
    });

    latch.await(15, TimeUnit.SECONDS);
    return true;
  }
}