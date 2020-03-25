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
package io.opentracing.contrib.specialagent.rule.spring.web40.copied;

import org.springframework.http.ResponseEntity;
import org.springframework.util.concurrent.ListenableFutureCallback;

import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.contrib.specialagent.OpenTracingApiUtil;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;

public class TracingListenableFutureCallback implements ListenableFutureCallback<Object> {
  private final ListenableFutureCallback<Object> callback;
  private final Span span;
  private final boolean finishSpan;

  public TracingListenableFutureCallback(ListenableFutureCallback<Object> callback, Span span,
      boolean finishSpan) {
    this.callback = callback;
    this.span = span;
    this.finishSpan = finishSpan;
  }

  @Override
  public void onFailure(Throwable ex) {
    if (finishSpan) {
      OpenTracingApiUtil.setErrorTag(span, ex);
      span.finish();
    }
    if (callback != null) {
      try (Scope scope = GlobalTracer.get().activateSpan(span)) {
        callback.onFailure(ex);
      }
    }
  }

  @Override
  public void onSuccess(Object result) {
    if (finishSpan) {
      if (result instanceof ResponseEntity) {
        ResponseEntity<?> responseEntity = (ResponseEntity<?>) result;
        span.setTag(Tags.HTTP_STATUS, responseEntity.getStatusCode().value());
      }
      span.finish();
    }
    if (callback != null) {
      try (Scope scope = GlobalTracer.get().activateSpan(span)) {
        callback.onSuccess(result);
      }
    }
  }
}