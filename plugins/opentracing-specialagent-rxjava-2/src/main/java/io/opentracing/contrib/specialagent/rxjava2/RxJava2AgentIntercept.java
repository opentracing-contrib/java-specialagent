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
package io.opentracing.contrib.specialagent.rxjava;


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
  private static boolean isTracingEnabled;

  @SuppressWarnings("unchecked")
  public static Object[] enter(Object thiz, Object... arg) {
    if (arg == null || arg[0] == null || arg[0].getClass().getPackage().getName()
        .startsWith("io.reactivex.internal")) {
      return null;
    }
    if (arg[0] instanceof TracingConsumer) {
      return null;
    }
    if (!isTracingEnabled) {
      isTracingEnabled = true;
      TracingRxJava2Utils.enableTracing();
    }
    if (arg[0] instanceof Observer) {
      return new Object[]{new TracingObserver((Observer) arg[0], "observer", GlobalTracer.get())};
    } else if (arg[0] instanceof Consumer) {
      Observable observable = (Observable) thiz;

      TracingConsumer tracingConsumer = null;
      if (arg.length == 1) {
        tracingConsumer = new TracingConsumer<>((Consumer<? super Object>) arg[0],
            "consumer", GlobalTracer.get());
      } else if (arg.length == 2) {
        tracingConsumer = new TracingConsumer<>((Consumer<? super Object>) arg[0],
            (Consumer<? super Throwable>) arg[1],
            "consumer", GlobalTracer.get());
      } else if (arg.length == 3) {
        tracingConsumer = new TracingConsumer<>((Consumer<? super Object>) arg[0],
            (Consumer<? super Throwable>) arg[1],
            (Action) arg[2],
            "consumer", GlobalTracer.get());
      } else if (arg.length == 4) {
        tracingConsumer = new TracingConsumer<>((Consumer<? super Object>) arg[0],
            (Consumer<? super Throwable>) arg[1],
            (Action) arg[2],
            (Consumer<? super Disposable>) arg[3],
            "consumer", GlobalTracer.get());
      }

      if(tracingConsumer != null) {
        observable.subscribe(tracingConsumer);
      }

      return new Object[]{null};


    }
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