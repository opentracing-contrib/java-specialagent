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

package io.opentracing.contrib.specialagent;

import io.opentracing.Scope;
import io.opentracing.Span;

/**
 * Thread local holder for Span, Scope and counter to control stack of calls.
 */
public class LocalSpanContext {
  private static final ThreadLocal<LocalSpanContext> instance = new ThreadLocal<>();
  private final Span span;
  private final Scope scope;
  private int counter = 1;

  private LocalSpanContext(final Span span, final Scope scope) {
    this.span = span;
    this.scope = scope;
  }

  public static LocalSpanContext get() {
    return instance.get();
  }

  public static void set(final Span span, final Scope scope) {
    instance.set(new LocalSpanContext(span, scope));
  }

  public static void remove() {
    instance.remove();
  }

  public Span getSpan() {
    return span;
  }

  public void increment() {
    ++counter;
  }

  public int decrementAndGet() {
    return --counter;
  }

  public void closeAndFinish() {
    closeScope();
    if (span != null)
      span.finish();
  }

  public void closeScope() {
    instance.remove();
    if (scope != null)
      scope.close();
  }
}