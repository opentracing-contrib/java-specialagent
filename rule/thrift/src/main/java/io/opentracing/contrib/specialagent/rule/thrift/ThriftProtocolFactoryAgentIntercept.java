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

package io.opentracing.contrib.specialagent.rule.thrift;

import java.util.concurrent.ConcurrentLinkedQueue;

import org.apache.thrift.protocol.TProtocol;

import io.opentracing.Tracer;
import io.opentracing.thrift.SpanHolder;
import io.opentracing.thrift.SpanProtocol;
import io.opentracing.util.GlobalTracer;

public class ThriftProtocolFactoryAgentIntercept {
  private static final ConcurrentLinkedQueue<SpanHolder> spanHolders = new ConcurrentLinkedQueue<>();

  @SuppressWarnings("deprecation")
  public static Object exit(final Object protocol) {
    if (!callerHasClass("org.apache.thrift.async.TAsyncMethodCall", 5))
      return protocol;

    final Tracer tracer = GlobalTracer.get();
    final SpanHolder spanHolder;
    if (Thread.currentThread().getName().startsWith("TAsyncClientManager#SelectorThread")) {
      spanHolder = spanHolders.poll();
      if (spanHolder != null) {
        tracer.scopeManager().activate(spanHolder.getSpan(), true);
      }
    }
    else {
      spanHolder = new SpanHolder();
      spanHolders.add(spanHolder);
    }

    return new SpanProtocol((TProtocol)protocol, tracer, spanHolder, false);
  }

  // FIXME: Look at AgentRuleUtil#callerEquals
  static boolean callerHasClass(final String className, final int frameMaxIndex) {
    final StackTraceElement[] stackTraceElements = Thread.currentThread().getStackTrace();
    final int limit = frameMaxIndex + 2;
    for (int i = 2; i < limit; ++i) {
      if (stackTraceElements.length < i)
        return false;

      if (stackTraceElements[i].getClassName().equals(className))
        return true;
    }

    return false;
  }
}