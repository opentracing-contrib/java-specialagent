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
import java.util.Map;

import org.springframework.messaging.MessageHeaders;

import io.opentracing.Tracer;
import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMap;

/**
 * A TextMap carrier for use with Tracer.extract() to extract tracing context
 * from Spring messaging {@link MessageHeaders}.
 *
 * @see Tracer#extract(Format,Object)
 */
public final class TextMapExtractAdapter implements TextMap {
  private final Map<String,String> headers = new HashMap<>();

  public TextMapExtractAdapter(final MessageHeaders headers) {
    for (Map.Entry<String,Object> entry : headers.entrySet())
      this.headers.put(entry.getKey(), entry.getValue().toString());
  }

  @Override
  public Iterator<Map.Entry<String,String>> iterator() {
    return headers.entrySet().iterator();
  }

  @Override
  public void put(final String key, final String value) {
    throw new UnsupportedOperationException(TextMapExtractAdapter.class.getName() + " can only be used with Tracer.inject()");
  }
}