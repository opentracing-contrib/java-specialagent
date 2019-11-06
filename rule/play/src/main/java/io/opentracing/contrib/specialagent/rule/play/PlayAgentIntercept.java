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

import io.opentracing.References;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.Tracer.SpanBuilder;
import io.opentracing.propagation.Format;
import io.opentracing.propagation.Format.Builtin;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import java.util.HashMap;
import java.util.Map;
import play.api.mvc.Action;
import play.api.mvc.Request;
import play.api.mvc.Result;
import scala.concurrent.Future;

public class PlayAgentIntercept {
  private static final ThreadLocal<Context> contextHolder = new ThreadLocal<>();
  static final String COMPONENT_NAME = "play";

  private static class Context {
    private Span span;
    private Scope scope;
    private int counter = 1;
  }


  public static Object applyStart(Object arg0) {
    if (contextHolder.get() != null) {
      ++contextHolder.get().counter;
      return arg0;
    }

    Request request = (Request) arg0;

    final Tracer tracer = GlobalTracer.get();


    final SpanBuilder spanBuilder = tracer.buildSpan(request.method())
        .withTag(Tags.COMPONENT, COMPONENT_NAME)
        .withTag(Tags.SPAN_KIND, Tags.SPAN_KIND_SERVER)
        .withTag(Tags.HTTP_METHOD, request.method())
        .withTag(Tags.HTTP_URL,
            (request.secure() ? "https://" : "http://") + request.host() + request.uri());

    final SpanContext parent = tracer
        .extract(Builtin.HTTP_HEADERS, new HttpHeadersExtractAdapter(request.headers()));

    if(parent != null) {
      spanBuilder.asChildOf(parent);
    }

    final Span span = spanBuilder.start();

    final Context context = new Context();
    contextHolder.set(context);
    context.span = span;
    context.scope = tracer.activateSpan(span);

    return arg0;
  }

  @SuppressWarnings("unchecked")
  public static void applyEnd(final Object thiz, final Object returned, final Throwable thrown) {
    final Context context = contextHolder.get();
    if (context == null)
      return;

    if (--context.counter != 0)
      return;

    final Span span = context.span;
    context.scope.close();
    contextHolder.remove();

    if (thrown != null) {
      onError(thrown, span);
      span.finish();
      return;
    }

    play.api.mvc.Action thisAction = (Action) thiz;

    Future<Result> future = (Future<Result>) returned;
    future.onComplete(
        res -> {
          if(res.isFailure()) {
            onError(res.failed().get(), span);
          } else {
            span.setTag(Tags.HTTP_STATUS, res.get().header().status());
          }
          span.finish();
          return null;
        },
        ((Action) thisAction).executionContext());

  }

  static void onError(final Throwable t, final Span span) {
    Tags.ERROR.set(span, Boolean.TRUE);
    if (t != null)
      span.log(errorLogs(t));
  }

  private static Map<String,Object> errorLogs(final Throwable t) {
    final Map<String,Object> errorLogs = new HashMap<>(2);
    errorLogs.put("event", Tags.ERROR.getKey());
    errorLogs.put("error.object", t);
    return errorLogs;
  }
}