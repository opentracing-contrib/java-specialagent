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
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Method;
import org.aopalliance.intercept.MethodInvocation;

public class TracingMethodInvocation implements MethodInvocation {
  private final MethodInvocation invocation;

  public TracingMethodInvocation(MethodInvocation invocation) {
    this.invocation = invocation;
  }

  @Override
  public Method getMethod() {
    return invocation.getMethod();
  }

  @Override
  public Object[] getArguments() {
    return invocation.getArguments();
  }

  @Override
  public Object proceed() throws Throwable {
    Span span = GlobalTracer.get().buildSpan(invocation.getMethod().getName())
        .withTag(Tags.COMPONENT.getKey(), "spring-async")
        .withTag("class", invocation.getMethod().getDeclaringClass().getSimpleName())
        .withTag("method", invocation.getMethod().getName())
        .start();

    try (Scope ignored = GlobalTracer.get().activateSpan(span)) {
      return invocation.proceed();
    } catch (Exception ex) {
      SpringSchedulingAgentIntercept.captureException(span, ex);
      throw ex;
    } finally {
      span.finish();
    }
  }

  @Override
  public Object getThis() {
    return invocation.getThis();
  }

  @Override
  public AccessibleObject getStaticPart() {
    return invocation.getStaticPart();
  }

}
