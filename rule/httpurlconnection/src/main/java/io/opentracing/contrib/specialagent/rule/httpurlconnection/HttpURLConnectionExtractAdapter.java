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

package io.opentracing.contrib.specialagent.rule.httpurlconnection;

import java.net.HttpURLConnection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.WeakHashMap;

import io.opentracing.propagation.TextMap;

public final class HttpURLConnectionExtractAdapter implements TextMap {
  private final WeakHashMap<String,String> map = new WeakHashMap<>();

  public HttpURLConnectionExtractAdapter(final HttpURLConnection connection) {
    final Map<String,List<String>> requestProperties = connection.getRequestProperties();
    if (requestProperties == null)
      return;

    for (final Entry<String,List<String>> entry : requestProperties.entrySet()) {
      final List<String> value = entry.getValue();
      if (value != null && value.size() == 1)
        map.put(entry.getKey(), value.get(0));
    }
  }

  @Override
  public Iterator<Map.Entry<String,String>> iterator() {
    return map.entrySet().iterator();
  }

  @Override
  public void put(final String key, final String value) {
    throw new UnsupportedOperationException("This class should be used only with Tracer.inject()");
  }
}