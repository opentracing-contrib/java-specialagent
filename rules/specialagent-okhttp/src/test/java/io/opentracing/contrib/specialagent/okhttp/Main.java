/* Copyright 2018 The OpenTracing Authors
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

package io.opentracing.contrib.specialagent.okhttp;

import static org.junit.Assert.*;

import java.io.IOException;
import java.lang.management.ManagementFactory;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

public class Main {
  public static int getPID() {
    final String pidAtHost = ManagementFactory.getRuntimeMXBean().getName();
    if (pidAtHost == null)
      return -1;

    try {
      return Integer.parseInt(pidAtHost.substring(0, pidAtHost.indexOf('@')));
    }
    catch (final NumberFormatException e) {
      return -1;
    }
  }

  public static void main(final String[] args) throws InterruptedException, IOException {
    System.err.println(System.getProperties());
    System.out.println(getPID());
    System.out.println("You have 5 seconds to attach...");
    Thread.sleep(5000);

    try (final MockWebServer server = new MockWebServer()) {
      while (true) {
        final OkHttpClient client = new OkHttpClient();
        try {
          server.enqueue(new MockResponse().setBody("hello, world!").setResponseCode(200));

          final HttpUrl httpUrl = server.url("/hello");

          final Request request = new Request.Builder().url(httpUrl).build();
          final Response response = client.newCall(request).execute();

          assertTrue(200 == response.code());
          assertTrue(1 == client.interceptors().size());
          assertTrue(1 == client.networkInterceptors().size());
          System.out.println("Ok");
        }
        catch (final Throwable e) {
          System.err.println(e.getClass().getSimpleName() + ": " + e.getMessage());
        }

        Thread.sleep(1000);
      }
    }
  }
}