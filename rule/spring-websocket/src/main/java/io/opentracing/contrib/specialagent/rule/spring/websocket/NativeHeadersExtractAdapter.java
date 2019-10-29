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
package io.opentracing.contrib.specialagent.rule.spring.websocket;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import io.opentracing.propagation.TextMap;

public final class NativeHeadersExtractAdapter implements TextMap {
  private final Map<String,String> headers = new HashMap<>();

  public NativeHeadersExtractAdapter(final Map<String,List<String>> nativeHeaders) {
    for (final Entry<String,List<String>> entry : nativeHeaders.entrySet()) {
      if (entry.getValue() != null && entry.getValue().size() == 1) {
        this.headers.put(entry.getKey(), entry.getValue().get(0));
      }
    }
  }

  @Override
  public Iterator<Map.Entry<String,String>> iterator() {
    return headers.entrySet().iterator();
  }

  @Override
  public void put(final String key, final String value) {
    throw new UnsupportedOperationException("TextMapInjectAdapter should only be used with Tracer.extract()");
  }
}
