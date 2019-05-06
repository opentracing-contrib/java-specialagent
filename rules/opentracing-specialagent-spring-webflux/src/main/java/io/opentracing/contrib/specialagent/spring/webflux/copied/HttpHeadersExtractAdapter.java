/**
 * Copyright 2016-2019 The OpenTracing Authors
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
package io.opentracing.contrib.specialagent.spring.webflux.copied;

import io.opentracing.propagation.TextMap;
import org.springframework.http.HttpHeaders;

import java.util.AbstractMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Tracer extract adapter for {@link HttpHeaders}.
 *
 * @author Csaba Kos
 */
class HttpHeadersExtractAdapter implements TextMap {
  private static final Stream<String> STREAM_OF_NULL = Stream.of(new String[]{ null });

  private final HttpHeaders httpHeaders;

  HttpHeadersExtractAdapter(final HttpHeaders httpHeaders) {
    this.httpHeaders = httpHeaders;
  }

  @Override
  public Iterator<Map.Entry<String, String>> iterator() {
    return httpHeaders.entrySet()
        .stream()
        .flatMap(entry -> getValuesStream(entry.getValue())
            .map(value -> newEntry(entry.getKey(), value)))
        .iterator();
  }

  private static Stream<String> getValuesStream(final List<String> values) {
    return values.isEmpty() ? STREAM_OF_NULL : values.stream();
  }

  private static Map.Entry<String, String> newEntry(final String key, final String value) {
    return new AbstractMap.SimpleImmutableEntry<>(key, value);
  }

  @Override
  public void put(String key, String value) {
    throw new UnsupportedOperationException("This class should be used only with Tracer.inject()!");
  }
}

