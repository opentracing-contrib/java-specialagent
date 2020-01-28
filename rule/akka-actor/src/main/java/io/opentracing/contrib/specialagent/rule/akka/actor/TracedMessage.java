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

package io.opentracing.contrib.specialagent.rule.akka.actor;

import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;
import java.util.Map;

public class TracedMessage<T> {
  private T message;
  private Map<String, String> headers;

  public TracedMessage(final T message, final Map<String, String> headers) {
    this.message = message;
    this.headers = headers;
  }

  public T getMessage() {
    return message;
  }

  public SpanContext spanContext(Tracer tracer) {
    return tracer.extract(Format.Builtin.TEXT_MAP_EXTRACT, () -> headers.entrySet().iterator());
  }
}