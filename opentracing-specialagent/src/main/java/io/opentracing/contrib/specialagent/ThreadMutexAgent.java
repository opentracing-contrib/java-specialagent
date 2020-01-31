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

package io.opentracing.contrib.specialagent;

import static net.bytebuddy.matcher.ElementMatchers.*;

import java.util.Arrays;

import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.agent.builder.AgentBuilder.Transformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType.Builder;
import net.bytebuddy.utility.JavaModule;

public class ThreadMutexAgent extends AgentRule {
  @Override
  public Iterable<? extends AgentBuilder> buildAgent(final AgentBuilder builder) {
    return Arrays.asList(builder
      .type(isSubTypeOf(Thread.class))
      .transform(new Transformer() {
        @Override
        public Builder<?> transform(final Builder<?> builder, final TypeDescription typeDescription, final ClassLoader classLoader, final JavaModule module) {
          return builder.visit(Advice.to(ThreadMutexAgent.class).on(named("start")));
        }}));
  }

  public static boolean isFromClassLoader(ClassLoader classLoader, final ClassLoader from) {
    if (classLoader == null)
      return false;

    boolean result;
    while (!(result = (classLoader == from)) && (classLoader = classLoader.getParent()) != null);
    return result;
  }

  @Advice.OnMethodEnter
  public static void enter(final @Advice.Origin String origin, final @Advice.This Thread thiz) {
    if (AgentRuleUtil.tracerClassLoader == null)
      return;

    if (!isEnabled(ThreadMutexAgent.class, origin)) {
      tracerThreadIds.add(thiz.getId());
      return;
    }

    final Class<?>[] callStack = AgentRuleUtil.getExecutionStack();
    for (final Class<?> cls : callStack) {
      if (isFromClassLoader(cls.getClassLoader(), AgentRuleUtil.tracerClassLoader)) {
        tracerThreadIds.add(thiz.getId());
        break;
      }
    }
  }
}