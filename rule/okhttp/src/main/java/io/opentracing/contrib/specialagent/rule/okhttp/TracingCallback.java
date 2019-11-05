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
package io.opentracing.contrib.specialagent.rule.okhttp;

import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.util.GlobalTracer;
import java.io.IOException;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

public class TracingCallback implements Callback {
  private final Callback callback;

  public TracingCallback(Callback callback) {
    this.callback = callback;
  }

  @Override
  public void onFailure(Call call, IOException e) {
    callback.onFailure(call, e);
  }

  @Override
  public void onResponse(Call call, Response response) throws IOException {
    final Object tag = response.request().tag();
    if (tag instanceof Span) {
      try (Scope scope = GlobalTracer.get().activateSpan((Span) tag)) {
        callback.onResponse(call, response);
      }
    } else {
      callback.onResponse(call, response);
    }
  }
}
