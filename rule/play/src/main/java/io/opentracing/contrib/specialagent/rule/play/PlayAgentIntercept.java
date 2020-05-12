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

package io.opentracing.contrib.specialagent.rule.play;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.Tracer.SpanBuilder;
import io.opentracing.contrib.specialagent.LocalSpanContext;
import io.opentracing.contrib.specialagent.OpenTracingApiUtil;
import io.opentracing.propagation.Format.Builtin;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import play.api.mvc.Action;
import play.api.mvc.Request;
import play.api.mvc.Result;
import scala.Function1;
import scala.concurrent.Future;
import scala.util.Try;

public class PlayAgentIntercept {
  static final String COMPONENT_NAME = "play";

  public static void applyStart(final Object arg0) {
    if (LocalSpanContext.get(COMPONENT_NAME) != null) {
      LocalSpanContext.get(COMPONENT_NAME).increment();
      return;
    }

    final Request<?> request = (Request<?>)arg0;
    final Tracer tracer = GlobalTracer.get();
    final SpanBuilder spanBuilder = tracer.buildSpan(request.method())
      .withTag(Tags.COMPONENT, COMPONENT_NAME)
      .withTag(Tags.SPAN_KIND, Tags.SPAN_KIND_SERVER)
      .withTag(Tags.HTTP_METHOD, request.method())
      .withTag(Tags.HTTP_URL, (request.secure() ? "https://" : "http://") + request.host() + request.uri());

    final SpanContext parent = tracer.extract(Builtin.HTTP_HEADERS, new HttpHeadersExtractAdapter(request.headers()));
    if (parent != null)
      spanBuilder.asChildOf(parent);

    final Span span = spanBuilder.start();
    LocalSpanContext.set(COMPONENT_NAME, span, tracer.activateSpan(span));
  }

  @SuppressWarnings("unchecked")
  public static void applyEnd(final Object thiz, final Object returned, final Throwable thrown) {
    final LocalSpanContext context = LocalSpanContext.get(COMPONENT_NAME);
    if (context == null)
      return;

    if (context.decrementAndGet() != 0)
      return;

    final Span span = context.getSpan();
    context.closeScope();

    if (thrown != null) {
      OpenTracingApiUtil.setErrorTag(span, thrown);
      span.finish();
      return;
    }

    ((Future<Result>)returned).onComplete(new Function1<Try<Result>,Object>() {
      @Override
      public Object apply(final Try<Result> response) {
        if (response.isFailure()) {
          OpenTracingApiUtil.setErrorTag(span, response.failed().get());
        }
        else {
          span.setTag(Tags.HTTP_STATUS, response.get().header().status());
        }

        span.finish();
        return null;
      }
    }, ((Action<?>)thiz).executionContext());
  }
}