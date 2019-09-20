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

package io.opentracing.contrib.specialagent.thread;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.contrib.specialagent.BootProxyClassLoader;
import io.opentracing.util.GlobalTracer;

@SuppressWarnings("unchecked")
public class ThreadAgentIntercept {
  public static final Map<Long,Span> cache;
  private static final ThreadLocal<Scope> scopeHandler = new ThreadLocal<>();

  static {
    try {
      if (ThreadAgentIntercept.class.getClassLoader() != null)
        cache = (Map<Long,Span>)BootProxyClassLoader.INSTANCE.loadClass(ThreadAgentIntercept.class.getName()).getField("cache").get(null);
      else
        cache = new ConcurrentHashMap<>();
    }
    catch (final ClassNotFoundException | IllegalAccessException | NoSuchFieldException e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  public static void start(final Object thiz) {
    Thread thread = (Thread)thiz;
    final Span span = GlobalTracer.get().activeSpan();
    if (span != null)
      cache.put(thread.getId(), span);

  }

  public static void runEnter(final Object thiz) {
    final Thread thread = (Thread)thiz;
    final Span span = cache.get(thread.getId());
    if (span != null) {
      final Scope scope = GlobalTracer.get().activateSpan(span);
      scopeHandler.set(scope);
    }
  }

  public static void runExit(final Object thiz) {
    final Thread thread = (Thread)thiz;
    final Span span = cache.remove(thread.getId());
    final Scope scope = scopeHandler.get();
    if (scope != null)
      scope.close();
  }
}
