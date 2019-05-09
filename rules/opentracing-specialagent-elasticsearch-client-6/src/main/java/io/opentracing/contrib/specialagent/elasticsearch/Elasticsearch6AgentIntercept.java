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

package io.opentracing.contrib.specialagent.elasticsearch;

import org.elasticsearch.action.ActionListener;
import org.elasticsearch.client.RestClientBuilder;

import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.contrib.elasticsearch.common.SpanDecorator;
import io.opentracing.contrib.elasticsearch.common.TracingHttpClientConfigCallback;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;

public class Elasticsearch6AgentIntercept {
  public static void rest(final Object thiz) {
    ((RestClientBuilder)thiz).setHttpClientConfigCallback(new TracingHttpClientConfigCallback());
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  public static ActionListener<?> transport(final Object request, final Object listener) {
    final Tracer.SpanBuilder spanBuilder = GlobalTracer.get()
      .buildSpan(request.getClass().getSimpleName())
      .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT);

    final Span span = spanBuilder.start();
    SpanDecorator.onRequest(span);
    return new TracingResponseListener<>((ActionListener)listener, span);
  }
}