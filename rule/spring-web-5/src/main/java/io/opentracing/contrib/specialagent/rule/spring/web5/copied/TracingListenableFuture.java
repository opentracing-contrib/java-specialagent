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

import io.opentracing.Span;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.util.concurrent.FailureCallback;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.util.concurrent.ListenableFutureCallback;
import org.springframework.util.concurrent.SuccessCallback;

public class TracingListenableFuture implements ListenableFuture {
  private final ListenableFuture listenableFuture;
  private final Span span;

  public TracingListenableFuture(ListenableFuture listenableFuture, Span span) {
    this.listenableFuture = listenableFuture;
    this.span = span;
  }

  @Override
  public void addCallback(ListenableFutureCallback callback) {
    listenableFuture.addCallback(new TracingListenableFutureCallback(callback, span));
  }

  @Override
  public void addCallback(SuccessCallback successCallback, FailureCallback failureCallback) {
    listenableFuture.addCallback(new TracingSuccessCallback(successCallback, span), new TracingFailureCallback(failureCallback, span));
  }

  @Override
  public boolean cancel(boolean mayInterruptIfRunning) {
    return listenableFuture.cancel(mayInterruptIfRunning);
  }

  @Override
  public boolean isCancelled() {
    return listenableFuture.isCancelled();
  }

  @Override
  public boolean isDone() {
    return listenableFuture.isDone();
  }

  @Override
  public Object get() throws InterruptedException, ExecutionException {
    return listenableFuture.get();
  }

  @Override
  public Object get(long timeout, TimeUnit unit)
      throws InterruptedException, ExecutionException, TimeoutException {
    return listenableFuture.get(timeout, unit);
  }
}
