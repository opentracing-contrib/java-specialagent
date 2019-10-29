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

package io.opentracing.contrib.specialagent.rule.asynchttpclient;

import java.util.Iterator;
import java.util.Map.Entry;

import org.asynchttpclient.AsyncHandler;
import org.asynchttpclient.Request;

import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMap;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;

public class AsyncHttpClientAgentIntercept {
  private static final String COMPONENT_NAME = "java-asynchttpclient";

  public static Object enter(final Object request, final Object handler) {
    final Request req = (Request)request;
    final Tracer tracer = GlobalTracer.get();
    final Span span = tracer
      .buildSpan(req.getMethod())
      .withTag(Tags.COMPONENT.getKey(), COMPONENT_NAME)
      .withTag(Tags.HTTP_METHOD.getKey(), req.getMethod())
      .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT)
      .withTag(Tags.HTTP_URL.getKey(), req.getUrl()).start();

    tracer.inject(span.context(), Format.Builtin.HTTP_HEADERS, new TextMap() {
      @Override
      public Iterator<Entry<String,String>> iterator() {
        throw new UnsupportedOperationException("iterator not supported with Tracer.inject()");
      }

      @Override
      public void put(final String key, final String value) {
        req.getHeaders().add(key, value);
      }
    });

    return new TracingAsyncHandler(tracer, (AsyncHandler<?>)handler, span);
  }
}