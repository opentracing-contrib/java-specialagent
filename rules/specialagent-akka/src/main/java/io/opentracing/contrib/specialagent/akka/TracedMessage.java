/* Copyright 2018 The OpenTracing Authors
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
package io.opentracing.contrib.specialagent.akka;

import io.opentracing.Scope;
import io.opentracing.Span;

public final class TracedMessage<T> {

  private T message;
  private Span span;
  private Scope scope;

  public TracedMessage(T message, Span span, Scope scope) {
    this.message = message;
    this.span = span;
    this.scope = scope;
  }

  public Span span() {
    return span;
  }

  public void closeScope() {
    if (scope != null) {
      scope.close();
      scope = null;
    }
  }

  public T message() {
    return message;
  }
}
