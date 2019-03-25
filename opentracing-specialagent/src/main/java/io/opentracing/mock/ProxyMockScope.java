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

package io.opentracing.mock;

import io.opentracing.Scope;
import io.opentracing.Span;

public class ProxyMockScope implements Scope {
  final Scope mockScope;
  final Scope realScope;

  public ProxyMockScope(final Scope mockScope, final Scope realScope) {
    if (mockScope != null ? realScope == null : realScope != null)
      throw new IllegalStateException();

    this.mockScope = mockScope;
    this.realScope = realScope;
  }

  @Override
  public void close() {
    mockScope.close();
    realScope.close();
  }

  @Override
  public ProxyMockSpan span() {
    final Span mockSpan = mockScope.span();
    final Span realSpan = realScope.span();
    return new ProxyMockSpan((MockSpan)mockSpan, realSpan);
  }
}