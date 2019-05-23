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
package io.opentracing.contrib.specialagent.feign;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

import io.opentracing.propagation.TextMap;

/**
 * Inject adapter for HTTP headers see {@link io.opentracing.Tracer#inject}.
 *
 * @author Pavol Loffay
 */
class HttpHeadersInjectAdapter implements TextMap {

  private Map<String, Collection<String>> headers;

  public HttpHeadersInjectAdapter(Map<String, Collection<String>> headers) {
    if (headers == null) {
      throw new NullPointerException("Headers should not be null!");
    }

    this.headers = headers;
  }

  @Override
  public void put(String key, String value) {
    Collection<String> values = headers.get(key);
    if (values == null) {
      values = new ArrayList<>(1);
      headers.put(key, values);
    }

    values.add(value);
  }

  @Override
  public Iterator<Map.Entry<String, String>> iterator() {
    throw new UnsupportedOperationException("This class should be used only with tracer#inject()");
  }
}
