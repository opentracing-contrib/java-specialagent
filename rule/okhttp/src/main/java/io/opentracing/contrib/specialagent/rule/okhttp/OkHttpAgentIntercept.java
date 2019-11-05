/* Copyright 2018 The OpenTracing Authors
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
import io.opentracing.propagation.Format;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import java.util.HashMap;
import java.util.Map;
import okhttp3.Callback;
import okhttp3.Request;
import okhttp3.Request.Builder;
import okhttp3.Response;

public class OkHttpAgentIntercept {
  private static final ThreadLocal<Context> contextHolder = new ThreadLocal<>();
  private static final String COMPONENT_NAME = "okhttp";

  public static Object enqueue(Object arg0) {
    return new TracingCallback((Callback) arg0);
  }

  private static class Context {
    private Span span;
    private Scope scope;
    private int counter = 1;
  }

  public static Object executeStart(Object arg0) {
    if (contextHolder.get() != null) {
      ++contextHolder.get().counter;
      return arg0;
    }

    Request request = (Request) arg0;

    final Span span = GlobalTracer.get().buildSpan(request.method())
        .withTag(Tags.COMPONENT, COMPONENT_NAME)
        .withTag(Tags.HTTP_METHOD, request.method())
        .withTag(Tags.HTTP_URL, request.url().toString())
        .start();

    final Builder builder = request.newBuilder();
    GlobalTracer.get().inject(span.context(), Format.Builtin.HTTP_HEADERS, new RequestBuilderInjectAdapter(builder));

    final Context context = new Context();
    contextHolder.set(context);
    context.span = span;
    context.scope = GlobalTracer.get().activateSpan(span);

    return builder.tag(span).build();
  }

  public static void executeEnd(Object returned, Throwable thrown) {
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

    Response response = (Response) returned;
    span.setTag(Tags.HTTP_STATUS, response.code());
    span.finish();
  }

  private static void onError(final Throwable t, final Span span) {
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