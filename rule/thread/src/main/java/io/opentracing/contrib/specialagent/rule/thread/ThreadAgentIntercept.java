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

package io.opentracing.contrib.specialagent.rule.thread;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.contrib.specialagent.AgentRuleUtil;
import io.opentracing.util.GlobalTracer;

public class ThreadAgentIntercept {
  public static final Map<Long,Span> threadIdToSpan;
  private static final ThreadLocal<Scope> localScope = new ThreadLocal<>();

  static {
    if (ThreadAgentIntercept.class.getClassLoader() == null) {
      threadIdToSpan = new ConcurrentHashMap<>();
    }
    else {
      threadIdToSpan = AgentRuleUtil.getFieldInBootstrapClass(ThreadAgentIntercept.class, "threadIdToSpan");
    }
  }

  public static void start(final Thread thread) {
    final Span span = GlobalTracer.get().activeSpan();
    if (span != null)
      threadIdToSpan.put(thread.getId(), span);
  }

  public static void runEnter(final Thread thread) {
    final Span span = threadIdToSpan.get(thread.getId());
    if (span != null)
      localScope.set(GlobalTracer.get().activateSpan(span));
  }

  @SuppressWarnings("resource")
  public static void runExit(final Thread thread) {
    threadIdToSpan.remove(thread.getId());
    final Scope scope = localScope.get();
    if (scope != null)
      scope.close();
  }
}