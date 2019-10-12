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
package io.opentracing.contrib.specialagent.spring40.web.copied;

import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import java.util.HashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.util.concurrent.ListenableFutureCallback;

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
      captureException(span, ex);
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

  public static void captureException(final Span span, final Throwable t) {
    final Map<String, Object> exceptionLogs = new HashMap<>();
    exceptionLogs.put("event", Tags.ERROR.getKey());
    exceptionLogs.put("error.object", t);
    span.log(exceptionLogs);
    Tags.ERROR.set(span, true);
  }
}