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
import java.util.function.BiFunction;
import java.util.logging.Logger;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Operators;

public class FluxAgentIntercept {
  private static final Logger log = Logger.getLogger(FluxAgentIntercept.class.getName());
  public static final AtomicBoolean inited = new AtomicBoolean();

  public static void enter() {
    if (inited.get())
      return;

    synchronized (inited) {
      if (inited.get())
        return;

      try {
        Operators.class.getMethod("liftPublisher", BiFunction.class);
      } catch (NoSuchMethodException e) {
        log.warning("Reactor version is not supported");
        inited.set(true);
        return;
      }

      final Tracer tracer = GlobalTracer.get();
      Hooks.onEachOperator(TracedSubscriber.asOperator(tracer));
      Hooks.onLastOperator(TracedSubscriber.asOperator(tracer));
      inited.set(true);
    }
  }
}
