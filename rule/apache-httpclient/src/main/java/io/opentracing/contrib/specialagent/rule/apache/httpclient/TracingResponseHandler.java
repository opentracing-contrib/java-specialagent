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

package io.opentracing.contrib.specialagent.rule.apache.httpclient;

import java.io.IOException;

import org.apache.http.HttpResponse;
import org.apache.http.client.ResponseHandler;

import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;

public class TracingResponseHandler<T> implements ResponseHandler<T> {
  private final ResponseHandler<T> handler;
  private final Span span;

  public TracingResponseHandler(final ResponseHandler<T> handler, final Span span) {
    this.handler = handler;
    this.span = span;
  }

  @Override
  public T handleResponse(final HttpResponse response) throws IOException {
    try (final Scope scope = GlobalTracer.get().activateSpan(span)) {
      return handler.handleResponse(response);
    }
    finally {
      Tags.HTTP_STATUS.set(span, response.getStatusLine().getStatusCode());
    }
  }
}