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

package io.opentracing.contrib.specialagent;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.modifier.Visibility;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.DynamicType.Unloaded;
import net.bytebuddy.implementation.MethodCall;
import net.bytebuddy.pool.TypePool;

public class DynamicAdvice {
  private Class<?> onEnter;
  private Class<?> onExit;

  public DynamicAdvice(final Class<?> advice) {
    TypePool.Default.of(Thread.currentThread().getContextClassLoader()).describe("FIXME");
    for (final Method method : advice.getMethods()) {
      if (Modifier.isStatic(method.getModifiers())) {
        for (final Annotation annotation : method.getAnnotations()) {
          if (Advice.OnMethodEnter.class.equals(annotation.annotationType())) {
            if (onEnter != null)
              throw new IllegalArgumentException("Multiple methods with @Advice.OnMethodEnter not allowed: " + advice.getName() + "#" + onEnter.getName() + ", and: " + advice.getName() + "#" + method.getName());

            onEnter = buildInterceptor(method, annotation);
          }
          else if (Advice.OnMethodExit.class.equals(annotation.annotationType())) {
            if (onExit != null)
              throw new IllegalArgumentException("Multiple methods with @Advice.OnMethodExit not allowed: " + advice.getName() + "#" + onExit.getName() + ", and: " + advice.getName() + "#" + method.getName());

            onExit = buildInterceptor(method, annotation);
          }
        }
      }
    }
  }

  private static Class<?> buildInterceptor(final Method method, final Annotation annotation) {
    DynamicType.Builder.MethodDefinition.ParameterDefinition.Simple<?> builder = new ByteBuddy()
      .makeInterface()
      .name("MyInterface")
      .annotateType(annotation)
      .modifiers(Visibility.PUBLIC)
      .defineMethod(method.getName(), void.class, Visibility.PUBLIC);

    final Class<?>[] parameterTypes = method.getParameterTypes();
    final Annotation[][] parameterAnnotations = method.getParameterAnnotations();
    for (int i = 0; i < parameterTypes.length; ++i)
      builder = builder.withParameter(parameterTypes[i]).annotateParameter(parameterAnnotations[i]);

    final Unloaded<?> unloaded = builder
      .intercept(MethodCall.invoke(method).withAllArguments())
      .make();

    return unloaded
      .load(Thread.currentThread().getContextClassLoader().getClass().getClassLoader())
      .getLoaded();
  }

  Class<?> getOnEnter() {
    return this.onEnter;
  }

  Class<?> getOnExit() {
    return this.onExit;
  }
}