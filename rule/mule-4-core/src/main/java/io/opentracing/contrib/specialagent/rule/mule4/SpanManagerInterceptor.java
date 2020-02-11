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

package io.opentracing.contrib.specialagent.rule.mule4;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.mule.runtime.api.component.location.ComponentLocation;
import org.mule.runtime.api.interception.InterceptionAction;
import org.mule.runtime.api.interception.InterceptionEvent;
import org.mule.runtime.api.interception.ProcessorInterceptor;
import org.mule.runtime.api.interception.ProcessorParameterValue;

import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;

public class SpanManagerInterceptor implements ProcessorInterceptor {
  private static final String DOC_NAME = "doc:name";
  private static final String UNNAMED = "unnamed";

  @Override
  public CompletableFuture<InterceptionEvent> around(final ComponentLocation location, final Map<String,ProcessorParameterValue> parameters, final InterceptionEvent event, final InterceptionAction action) {
    final String correlationId = event.getCorrelationId();
    if (correlationId == null)
      return action.proceed();

    final Span span = SpanAssociations.get().retrieveSpan(correlationId);
    if (span == null)
      return action.proceed();

    final Tracer tracer = GlobalTracer.get();
    final Span processorSpan = tracer.buildSpan(getDocName(parameters)).asChildOf(span).start();
    Tags.COMPONENT.set(processorSpan, getComponentName(location));

    final Scope inScope = tracer.activateSpan(processorSpan);
    return action.proceed().exceptionally(exception -> {
      processorSpan.setTag(Tags.ERROR, true);
      if (exception != null)
        span.log(errorLogs(exception));

      throw new RuntimeException(exception);
    }).thenApply(finalEvent -> {
      inScope.close();
      processorSpan.finish();
      return finalEvent;
    });
  }

  public static String getComponentName(final ComponentLocation location) {
    return location.getComponentIdentifier().getIdentifier().getNamespace() + ":" + location.getComponentIdentifier().getIdentifier().getName();
  }

  public static String getDocName(final Map<String,ProcessorParameterValue> parameters) {
    final ProcessorParameterValue nameParam = parameters.get(DOC_NAME);
    return nameParam == null ? UNNAMED : nameParam.providedValue();
  }

  private static Map<String,Object> errorLogs(final Throwable t) {
    final Map<String,Object> errorLogs = new HashMap<>(2);
    errorLogs.put("event", Tags.ERROR.getKey());
    errorLogs.put("error.object", t);
    return errorLogs;
  }
}