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

package io.opentracing.contrib.specialagent.test.zuul;

import io.opentracing.contrib.specialagent.TestUtil.ComponentSpanCount;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.zuul.EnableZuulProxy;
import org.springframework.context.annotation.Bean;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import io.opentracing.contrib.specialagent.TestUtil;

@SpringBootApplication
@EnableZuulProxy
public class ZuulITest {
  public static void main(final String[] args) {
    SpringApplication.run(ZuulITest.class, args).close();
    TestUtil.checkSpan(true, new ComponentSpanCount("zuul", 1), new ComponentSpanCount("java-spring-rest-template", 1), new ComponentSpanCount("java-web-servlet", 1));
  }

  @Bean
  public CommandLineRunner commandLineRunner() {
    return new CommandLineRunner() {
      @Override
      public void run(final String ... args) {
        final RestTemplate restTemplate = new RestTemplate();
        final ResponseEntity<String> entity = restTemplate.getForEntity("http://localhost:8080", String.class);
        final int statusCode = entity.getStatusCode().value();
        if (200 != statusCode)
          throw new AssertionError("ERROR: response: " + statusCode);
      }
    };
  }
}