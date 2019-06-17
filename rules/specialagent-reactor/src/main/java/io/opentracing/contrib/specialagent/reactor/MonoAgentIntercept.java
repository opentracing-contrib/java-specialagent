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

package io.opentracing.contrib.specialagent.reactor;

import java.util.concurrent.atomic.AtomicBoolean;

import io.opentracing.Tracer;
import io.opentracing.contrib.reactor.TracedSubscriber;
import io.opentracing.util.GlobalTracer;
import reactor.core.publisher.Hooks;

public class MonoAgentIntercept {
  public static final AtomicBoolean inited = new AtomicBoolean();

  public static void enter() {
    if (inited.get())
      return;

    synchronized (inited) {
      if (inited.get())
        return;

      final Tracer tracer = GlobalTracer.get();
      Hooks.onEachOperator(TracedSubscriber.asOperator(tracer));
      Hooks.onLastOperator(TracedSubscriber.asOperator(tracer));
      inited.set(true);
    }
  }
}
