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

package io.opentracing.contrib.specialagent.spring.messaging;

import java.util.ArrayList;
import java.util.List;

import org.springframework.cloud.stream.annotation.EnableBinding;
import org.springframework.cloud.stream.annotation.StreamListener;
import org.springframework.cloud.stream.messaging.Sink;

@EnableBinding(Sink.class)
public class Receiver {
  private final List<String> receivedMessages = new ArrayList<>();

  @StreamListener(Sink.INPUT)
  public void receive(final byte[] message) {
    receivedMessages.add(new String(message));
  }

  public List<String> getReceivedMessages() {
    return receivedMessages;
  }

  public void clear() {
    receivedMessages.clear();
  }
}