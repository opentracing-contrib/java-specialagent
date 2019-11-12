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

package io.opentracing.contrib.specialagent.rule.playws;

import play.shaded.ahc.io.netty.handler.codec.http.HttpHeaders;
import io.opentracing.propagation.TextMap;
import java.util.Iterator;
import java.util.Map.Entry;

public class HttpHeadersInjectAdapter implements TextMap {
  private final HttpHeaders httpHeaders;

  public HttpHeadersInjectAdapter(final HttpHeaders httpHeaders) {
    this.httpHeaders = httpHeaders;
  }

  @Override
  public void put(final String key, final String value) {
    httpHeaders.add(key, value);
  }

  @Override
  public Iterator<Entry<String,String>> iterator() {
    throw new UnsupportedOperationException("This class should be used only with tracer#inject()");
  }
}