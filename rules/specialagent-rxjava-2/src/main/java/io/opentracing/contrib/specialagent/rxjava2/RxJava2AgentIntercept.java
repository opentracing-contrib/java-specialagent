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

package io.opentracing.contrib.specialagent.rxjava2;

import io.opentracing.rxjava2.TracingConsumer;
import io.opentracing.rxjava2.TracingObserver;
import io.opentracing.rxjava2.TracingRxJava2Utils;
import io.opentracing.util.GlobalTracer;
import io.reactivex.Observable;
import io.reactivex.Observer;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Action;
import io.reactivex.functions.Consumer;

public class RxJava2AgentIntercept {
  public static final Object NULL = new Object();
  private static boolean isTracingEnabled;

  @SuppressWarnings("unchecked")
  public static Object enter(final Object thiz, final int argc, final Object arg0, final Object arg1, final Object arg2, final Object arg3) {
    if (arg0 == null || arg0.getClass().getName().startsWith("io.reactivex.internal"))
      return NULL;

    if (arg0 instanceof TracingConsumer)
      return NULL;

    if (!isTracingEnabled) {
      isTracingEnabled = true;
      TracingRxJava2Utils.enableTracing();
    }

    if (arg0 instanceof Observer)
      return new TracingObserver<>((Observer<?>)arg0, "observer", GlobalTracer.get());

    if (!(arg0 instanceof Consumer))
      return NULL;

    final TracingConsumer<Object> tracingConsumer;
    if (argc == 1)
      tracingConsumer = new TracingConsumer<>((Consumer<Object>)arg0, "consumer", GlobalTracer.get());
    else if (argc == 2)
      tracingConsumer = new TracingConsumer<>((Consumer<Object>)arg0, (Consumer<Throwable>)arg1, "consumer", GlobalTracer.get());
    else if (argc == 3)
      tracingConsumer = new TracingConsumer<>((Consumer<Object>)arg0, (Consumer<Throwable>)arg1, (Action)arg2, "consumer", GlobalTracer.get());
    else if (argc == 4)
      tracingConsumer = new TracingConsumer<>((Consumer<Object>)arg0, (Consumer<Throwable>)arg1, (Action)arg2, (Consumer<Disposable>)arg3, "consumer", GlobalTracer.get());
    else
      tracingConsumer = null;

    if (tracingConsumer != null)
      ((Observable<Object>)thiz).subscribe(tracingConsumer);

    return null;
  }

  public static Object disposable() {
    return new Disposable() {
      @Override
      public void dispose() {
      }

      @Override
      public boolean isDisposed() {
        return true;
      }
    };
  }
}