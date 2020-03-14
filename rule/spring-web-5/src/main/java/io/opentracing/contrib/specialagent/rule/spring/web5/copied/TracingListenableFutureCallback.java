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
package io.opentracing.contrib.specialagent.rule.spring.web5.copied;

import org.springframework.util.concurrent.ListenableFutureCallback;

import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.util.GlobalTracer;

public class TracingListenableFutureCallback implements ListenableFutureCallback {
  private final ListenableFutureCallback callback;
  private final Span span;

  public TracingListenableFutureCallback(ListenableFutureCallback callback, Span span) {
    this.callback = callback;
    this.span = span;
  }

  @Override
  public void onFailure(Throwable ex) {
    if (callback != null) {
      try (Scope scope = GlobalTracer.get().activateSpan(span)) {
        callback.onFailure(ex);
      }
    }

  }

  @Override
  public void onSuccess(Object result) {
    if (callback != null) {
      try (Scope scope = GlobalTracer.get().activateSpan(span)) {
        callback.onSuccess(result);
      }
    }
  }
}
