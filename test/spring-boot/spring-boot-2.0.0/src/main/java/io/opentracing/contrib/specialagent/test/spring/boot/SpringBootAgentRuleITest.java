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

package io.opentracing.contrib.specialagent.test.spring.boot;

import static org.junit.Assert.*;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@SpringBootApplication
public class SpringBootAgentRuleITest {
  static {
    // Avoid: https://github.com/spring-projects/spring-boot/issues/3100
    System.setProperty("spring.devtools.restart.enabled", "false");
  }

  public static void main(final String[] args) {
    assertNull("If this is 'false', it means SpecialAgent set this to 'false', because no deferrers were found", System.getProperty("sa.init.defer"));
    SpringApplication.run(SpringBootAgentRuleITest.class);
    assertNull(System.getProperty("sa.init.defer"));
    System.exit(0);
  }
}