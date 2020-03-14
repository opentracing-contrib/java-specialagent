/**
 * Copyright 2017-2019 The OpenTracing Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.opentracing.contrib.specialagent.rule.spring.messaging.copied;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.springframework.integration.support.MutableMessageHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import io.opentracing.propagation.TextMap;

/**
 * @author <a href="mailto:gytis@redhat.com">Gytis Trikleris</a>
 */
public class MessageTextMap<T> implements TextMap {

  private final Message<T> message;

  private final MutableMessageHeaders headers;

  private final Set<String> byteHeaders;

  private final boolean isKafkaBinder;

  public MessageTextMap(Message<T> message, boolean isKafkaBinder) {
    this.message = message;
    this.headers = new MutableMessageHeaders(message.getHeaders());
    this.byteHeaders = new HashSet<>();
    this.isKafkaBinder = isKafkaBinder;
  }

  @Override
  public Iterator<Map.Entry<String, String>> iterator() {
    Map<String, String> stringHeaders = new HashMap<>(headers.size());
    headers.forEach((k, v) -> {
      if (v instanceof byte[]) {
        try {
          stringHeaders.put(k, new String((byte[])v));
          byteHeaders.add(k);
        } catch (Exception ex) {
          stringHeaders.put(k, String.valueOf(v));
        }
      } else {
        stringHeaders.put(k, String.valueOf(v));
      }
    });
    return stringHeaders.entrySet().iterator();
  }

  @Override
  public void put(String key, String value) {
    if (isKafkaBinder) {
      // for Kafka value must be byte array
      headers.put(key, value.getBytes());
    } else {
      headers.put(key, byteHeaders.contains(key) ? value.getBytes() : value);
    }
  }

  public Message<T> getMessage() {
    return MessageBuilder.fromMessage(message)
        .copyHeaders(headers)
        .build();
  }
}
