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

package io.opentracing.contrib.specialagent.spring.scheduling;

import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import java.util.HashMap;
import java.util.Map;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.scheduling.support.ScheduledMethodRunnable;

public class SpringSchedulingAgentIntercept {
  private static final ThreadLocal<Context> contextHolder = new ThreadLocal<>();

  private static class Context {
    private Scope scope;
    private Span span;
  }

  public static void enter(Object thiz) {
    ScheduledMethodRunnable runnable = (ScheduledMethodRunnable) thiz;
    Span span = GlobalTracer.get().buildSpan(runnable.getMethod().getName())
        .withTag(Tags.COMPONENT.getKey(), "spring-scheduled")
        .withTag("class", runnable.getTarget().getClass().getSimpleName())
        .withTag("method", runnable.getMethod().getName())
        .start();

    final Scope scope = GlobalTracer.get().activateSpan(span);
    final Context context = new Context();
    contextHolder.set(context);
    context.scope = scope;
    context.span = span;

  }

  public static void exit(Throwable thrown) {
    final Context context = contextHolder.get();
    if (context != null) {
      if (thrown != null) {
        captureException(context.span, thrown);
      }
      context.scope.close();
      context.span.finish();
      contextHolder.remove();
    }
  }

  static void captureException(Span span, Throwable ex) {
    Map<String, Object> exceptionLogs = new HashMap<>();
    exceptionLogs.put("event", Tags.ERROR.getKey());
    exceptionLogs.put("error.object", ex);
    span.log(exceptionLogs);
    Tags.ERROR.set(span, true);
  }


  public static Object invoke(Object arg) {
    MethodInvocation invocation = (MethodInvocation) arg;
    return new TracingMethodInvocation(invocation);
  }
}