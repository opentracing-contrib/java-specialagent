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

package io.opentracing.contrib.specialagent.rule.playws;

import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.contrib.common.WrapperProxy;
import io.opentracing.propagation.Format.Builtin;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import play.shaded.ahc.org.asynchttpclient.AsyncHandler;
import play.shaded.ahc.org.asynchttpclient.Request;

public class PlayWSAgentIntercept {
  static final String COMPONENT_NAME = "play-ws";

  public static Object executeStart(final Object arg0, final Object arg1) {
    final Request request = (Request)arg0;
    final AsyncHandler<?> asyncHandler = (AsyncHandler<?>)arg1;

    final Tracer tracer = GlobalTracer.get();
    final Span span = tracer.buildSpan(request.getMethod())
      .withTag(Tags.COMPONENT, COMPONENT_NAME)
      .withTag(Tags.SPAN_KIND, Tags.SPAN_KIND_CLIENT)
      .withTag(Tags.HTTP_METHOD, request.getMethod())
      .withTag(Tags.HTTP_URL, request.getUrl())
      .start();

    tracer.inject(span.context(), Builtin.HTTP_HEADERS, new HttpHeadersInjectAdapter(request.getHeaders()));
    return WrapperProxy.wrap(asyncHandler, new TracingAsyncHandler(asyncHandler, span));
  }
}