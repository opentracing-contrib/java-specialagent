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

package io.opentracing.contrib.specialagent.rule.spymemcached;

import java.util.HashMap;
import java.util.Map;

import io.opentracing.Span;
import io.opentracing.contrib.specialagent.OpenTracingApiUtil;
import net.spy.memcached.ops.OperationCallback;
import net.spy.memcached.ops.OperationStatus;

public class TracingOperationCallback implements OperationCallback {
  protected final OperationCallback operationCallback;
  private final Span span;

  public TracingOperationCallback(final OperationCallback operationCallback, final Span span) {
    this.operationCallback = operationCallback;
    this.span = span;
  }

  @Override
  public void receivedStatus(final OperationStatus status) {
    final Map<String,Object> event = new HashMap<>();
    event.put("status", status.getStatusCode());
    span.log(event);
    operationCallback.receivedStatus(status);
  }

  @Override
  public void complete() {
    try {
      operationCallback.complete();
    }
    finally {
      span.finish();
    }
  }

  void onError(final Throwable thrown) {
    OpenTracingApiUtil.setErrorTag(span, thrown);
    span.finish();
  }
}