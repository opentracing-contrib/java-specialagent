/* Copyright 2020 The OpenTracing Authors
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

package io.opentracing.contrib.specialagent.rule.netty;

import java.util.Iterator;
import java.util.Map.Entry;

import io.netty.handler.codec.http.HttpHeaders;
import io.opentracing.propagation.TextMap;

public final class NettyExtractAdapter implements TextMap {
  private final HttpHeaders headers;

  public NettyExtractAdapter(final HttpHeaders headers) {
    this.headers = headers;
  }

  @Override
  public Iterator<Entry<String,String>> iterator() {
    return headers.iteratorAsString();
  }

  @Override
  public void put(final String key, final String value) {
    throw new UnsupportedOperationException("This class should be used only with Tracer.inject()");
  }
}