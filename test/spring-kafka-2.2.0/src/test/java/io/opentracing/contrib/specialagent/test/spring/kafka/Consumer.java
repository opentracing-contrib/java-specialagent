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
package io.opentracing.contrib.specialagent.test.spring.kafka;

import io.opentracing.contrib.specialagent.TestUtil;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Service;

@Service
public class Consumer {

  @KafkaListener(topics = "users")
  @SendTo("reply")
  public String consume(String message) {
    System.out.println(String.format("1. #### -> Consumed message -> %s", message));
    TestUtil.checkActiveSpan();
    return message.toUpperCase();
  }

  @KafkaListener(topics = "reply")
  public void consume2(String message) {
    System.out.println(String.format("2. #### -> Consumed message -> %s", message));
    TestUtil.checkActiveSpan();
  }
}
