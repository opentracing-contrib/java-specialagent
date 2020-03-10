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

package io.opentracing.contrib.specialagent.rule.akka.http;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import akka.http.javadsl.model.HttpHeader;
import akka.http.javadsl.model.HttpRequest;
import io.opentracing.propagation.TextMap;

public class HttpHeadersExtractAdapter implements TextMap {
  private final Map<String,String> map = new HashMap<>();

  HttpHeadersExtractAdapter(final HttpRequest httpRequest) {
    for (final HttpHeader header : httpRequest.getHeaders()) {
      map.put(header.name(), header.value());
    }
  }

  @Override
  public Iterator<Map.Entry<String,String>> iterator() {
    return map.entrySet().iterator();
  }

  @Override
  public void put(final String key, final String value) {
    throw new UnsupportedOperationException("This class should be used only with Tracer.inject()!");
  }
}