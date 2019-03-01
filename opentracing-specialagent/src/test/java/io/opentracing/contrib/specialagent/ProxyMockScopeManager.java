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

package io.opentracing.contrib.specialagent;

import io.opentracing.Scope;
import io.opentracing.ScopeManager;
import io.opentracing.Span;

public class ProxyMockScopeManager implements ScopeManager {
  final ScopeManager mockScopeManager;
  final ScopeManager realScopeManager;

  public ProxyMockScopeManager(final ScopeManager mockScopeManager, final ScopeManager realScopeManager) {
    if (mockScopeManager != null ? realScopeManager == null : realScopeManager != null)
      throw new IllegalStateException();

    this.mockScopeManager = mockScopeManager;
    this.realScopeManager = realScopeManager;
  }

  @Override
  public ProxyMockScope activate(final Span span, final boolean finishSpanOnClose) {
    final Scope mockScope = mockScopeManager.activate(span, finishSpanOnClose);
    final Scope realScope = realScopeManager.activate(span, finishSpanOnClose);
    return new ProxyMockScope(mockScope, realScope);
  }

  @Override
  public ProxyMockScope active() {
    final Scope mockScope = mockScopeManager.active();
    final Scope realScope = realScopeManager.active();
    return new ProxyMockScope(mockScope, realScope);
  }
}