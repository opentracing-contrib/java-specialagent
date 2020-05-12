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

import java.util.HashMap;
import java.util.Map;

import io.opentracing.Scope;
import io.opentracing.Span;

/**
 * Thread local map holder for Span, Scope and counter to control stack of
 * calls. Map is used to avoid suppressing of creation of new span when active
 * span of another component exists. Key of the map is component name.
 */
public class LocalSpanContext {
  private static final ThreadLocal<Map<String,LocalSpanContext>> instance = new ThreadLocal<>();

  private final String name;
  private final Span span;
  private final Scope scope;
  private int counter = 1;

  private LocalSpanContext(final String name, final Span span, final Scope scope) {
    this.name = name;
    this.span = span;
    this.scope = scope;
  }

  public static LocalSpanContext get(final String name) {
    final Map<String,LocalSpanContext> map = instance.get();
    return map == null ? null : map.get(name);
  }

  public static void set(final String name, final Span span, final Scope scope) {
    if (instance.get() == null)
      instance.set(new HashMap<String,LocalSpanContext>());

    instance.get().put(name, new LocalSpanContext(name, span, scope));
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
    final Map<String,LocalSpanContext> map = instance.get();
    if (map != null) {
      map.remove(name);
      if (map.isEmpty())
        instance.remove();
    }

    if (scope != null)
      scope.close();
  }
}