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

import io.opentracing.Span;
import net.spy.memcached.ops.GetOperation;

public class TracingGetOperationCallback extends TracingOperationCallback implements GetOperation.Callback {
  public TracingGetOperationCallback(final GetOperation.Callback callback, final Span span) {
    super(callback, span);
  }

  @Override
  public void gotData(final String key, final int flags, final byte[] data) {
    ((GetOperation.Callback)operationCallback).gotData(key, flags, data);
  }
}