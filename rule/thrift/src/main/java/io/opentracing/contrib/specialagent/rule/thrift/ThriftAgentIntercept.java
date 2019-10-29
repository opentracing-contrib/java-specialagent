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

import org.apache.thrift.TProcessor;

import io.opentracing.Scope;
import io.opentracing.thrift.DefaultClientSpanDecorator;
import io.opentracing.thrift.SpanProcessor;
import io.opentracing.util.GlobalTracer;

public class ThriftAgentIntercept {
  public static void onComplete() {
    final Scope scope = GlobalTracer.get().scopeManager().active();
    if (scope != null)
      scope.close();
  }

  public static void onError(final Object exception) {
    final Scope scope = GlobalTracer.get().scopeManager().active();
    if (scope != null) {
      new DefaultClientSpanDecorator().onError((Throwable)exception, scope.span());
      scope.close();
    }
  }

  public static Object getProcessor(final Object processor) {
    return new SpanProcessor((TProcessor)processor);
  }
}