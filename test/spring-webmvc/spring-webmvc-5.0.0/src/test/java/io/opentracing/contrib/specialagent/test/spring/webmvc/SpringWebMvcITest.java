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

package io.opentracing.contrib.specialagent.test.spring.webmvc;

import java.net.HttpURLConnection;
import java.net.URL;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

import io.opentracing.contrib.specialagent.TestUtil;
import io.opentracing.contrib.specialagent.TestUtil.ComponentSpanCount;

@SpringBootApplication
public class SpringWebMvcITest {
  public static void main(final String[] args) {
    SpringApplication.run(SpringWebMvcITest.class, args).close();
    TestUtil.checkSpan(true, new ComponentSpanCount("java-web-servlet", 1), new ComponentSpanCount("http-url-connection", 1));
  }

  @Bean
  public RestTemplate restTemplate() {
    return new RestTemplate();
  }

  @Bean
  public CommandLineRunner commandLineRunner() {
    return new CommandLineRunner() {
      @Override
      public void run(final String ... args) throws Exception {
        final URL obj = new URL("http://localhost:8080");
        final HttpURLConnection connection = (HttpURLConnection)obj.openConnection();
        connection.setRequestMethod("GET");
        final int responseCode = connection.getResponseCode();
        if (200 != responseCode)
          throw new AssertionError("ERROR: response: " + responseCode);
      }
    };
  }
}