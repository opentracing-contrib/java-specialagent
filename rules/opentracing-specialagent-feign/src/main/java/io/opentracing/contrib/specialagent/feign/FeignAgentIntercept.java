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

package io.opentracing.contrib.specialagent.feign;

import feign.Client;
import feign.Feign;
import feign.Feign.Builder;
import feign.opentracing.TracingClient;
import io.opentracing.util.GlobalTracer;

public class FeignAgentIntercept {
  public static Object client(final Object client) {
    return client instanceof TracingClient ? client : new TracingClient((Client)client, GlobalTracer.get());
  }

  public static Object builder(final Object returned) {
    final Feign.Builder builder = (Builder)returned;
    builder.client(new TracingClient(new Client.Default(null, null), GlobalTracer.get()));
    return returned;
  }
}