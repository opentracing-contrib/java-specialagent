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

package io.opentracing.contrib.specialagent.test.spring.webflux;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;

import io.opentracing.contrib.specialagent.TestUtil;

@SpringBootApplication
public class SpringWebFluxITest {
  public static void main(final String[] args) {
    TestUtil.initTerminalExceptionHandler();
    SpringApplication.run(SpringWebFluxITest.class, args).close();

    TestUtil.checkSpan("java-spring-webclient", 4, true);
  }

  @Bean
  public CommandLineRunner commandLineRunner() {
    return new CommandLineRunner() {
      @Override
      public void run(final String ... args) {
        final WebClient client = WebClient.builder().baseUrl("http://localhost:8080").build();

        final ClientResponse response = client.get().exchange().block();
        final int responseCode = response.statusCode().value();
        if (200 != responseCode)
          throw new AssertionError("ERROR: response: " + responseCode);

        final String entity = response.bodyToMono(String.class).block();
        if (!"WebFlux".equals(entity))
          throw new AssertionError("ERROR: response: " + entity);
      }
    };
  }
}