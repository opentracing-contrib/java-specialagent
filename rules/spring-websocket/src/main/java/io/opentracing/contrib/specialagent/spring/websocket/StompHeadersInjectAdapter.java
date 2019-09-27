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

package io.opentracing.contrib.specialagent.spring.websocket;

import java.util.Iterator;
import java.util.Map;

import org.springframework.messaging.simp.stomp.StompHeaders;

import io.opentracing.propagation.TextMap;

public class StompHeadersInjectAdapter implements TextMap {
  private final StompHeaders headers;

  public StompHeadersInjectAdapter(final StompHeaders headers) {
    this.headers = headers;
  }

  @Override
  public Iterator<Map.Entry<String,String>> iterator() {
    throw new UnsupportedOperationException(StompHeadersInjectAdapter.class.getName() + " should only be used with Tracer.inject()");
  }

  @Override
  public void put(final String key, final String value) {
    headers.add(key, value);
  }
}