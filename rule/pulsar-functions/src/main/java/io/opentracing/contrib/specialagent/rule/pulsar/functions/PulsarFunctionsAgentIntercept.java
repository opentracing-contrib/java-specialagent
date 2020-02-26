/* Copyright 2020 The OpenTracing Authors
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

package io.opentracing.contrib.specialagent.rule.pulsar.functions;

import io.opentracing.contrib.specialagent.LocalSpanContext;
import java.util.HashMap;

import org.apache.pulsar.functions.api.Record;
import org.apache.pulsar.functions.instance.JavaExecutionResult;

import io.opentracing.References;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.Tracer.SpanBuilder;
import io.opentracing.propagation.Format.Builtin;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;

public class PulsarFunctionsAgentIntercept {
  static final String COMPONENT_NAME = "java-pulsar-functions";


  public static void handleMessageEnter(final Object function, final Object contextArg, final Object arg0) {
    final Tracer tracer = GlobalTracer.get();
    final SpanBuilder spanBuilder = tracer
      .buildSpan(getFunctionName(function, contextArg))
      .withTag(Tags.COMPONENT, COMPONENT_NAME)
      .withTag(Tags.SPAN_KIND, Tags.SPAN_KIND_SERVER);

    if (arg0 != null) {
      final Record<?> record = (Record<?>)arg0;
      final SpanContext spanContext = tracer.extract(Builtin.TEXT_MAP, new TextMapExtractAdapter(record.getProperties()));
      if (spanContext != null)
        spanBuilder.addReference(References.FOLLOWS_FROM, spanContext);
    }

    final Span span = spanBuilder.start();
    final Scope scope = tracer.activateSpan(span);

    LocalSpanContext.set(span, scope);
  }

  private static String getFunctionName(final Object function, final Object contextArg) {
    if (contextArg != null) {
      final org.apache.pulsar.functions.api.Context contextImpl = (org.apache.pulsar.functions.api.Context)contextArg;
      if (contextImpl.getFunctionName() != null)
        return contextImpl.getFunctionName();
    }

    final String simpleName = function.getClass().getSimpleName();
    if (simpleName.length() == 0)
      return function.getClass().getName();

    return simpleName;
  }

  public static void handleMessageEnd(final Object returned, final Throwable thrown) {
    final LocalSpanContext context = LocalSpanContext.get();
    if (context == null)
      return;

    context.closeScope();
    final Span span = context.getSpan();

    if (thrown != null) {
      onError(thrown, span);
      span.finish();
      return;
    }

    final JavaExecutionResult result = (JavaExecutionResult)returned;
    if (result.getSystemException() != null)
      onError(result.getSystemException(), span);
    else if (result.getUserException() != null)
      onError(result.getUserException(), span);

    span.finish();
  }

  private static void onError(final Throwable t, final Span span) {
    Tags.ERROR.set(span, Boolean.TRUE);
    if (t != null)
      span.log(errorLogs(t));
  }

  private static HashMap<String,Object> errorLogs(final Throwable t) {
    final HashMap<String,Object> errorLogs = new HashMap<>(2);
    errorLogs.put("event", Tags.ERROR.getKey());
    errorLogs.put("error.object", t);
    return errorLogs;
  }
}