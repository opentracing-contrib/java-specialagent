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

package io.opentracing.contrib.specialagent;

import static net.bytebuddy.matcher.ElementMatchers.*;

import java.lang.instrument.Instrumentation;

import io.opentracing.Scope;
import io.opentracing.ScopeManager;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.Tracer.SpanBuilder;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.agent.builder.AgentBuilder.InitializationStrategy;
import net.bytebuddy.agent.builder.AgentBuilder.RedefinitionStrategy;
import net.bytebuddy.agent.builder.AgentBuilder.Transformer;
import net.bytebuddy.agent.builder.AgentBuilder.TypeStrategy;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType.Builder;
import net.bytebuddy.utility.JavaModule;

public class TracerMutexAgent {
  private static final Logger logger = Logger.getLogger(TracerMutexAgent.class);

  public static void premain(final Instrumentation inst) {
    if (logger.isLoggable(Level.FINE))
      logger.fine("\n<<<<<<<<<<<<<<<<<<<< Installing MutexAgent >>>>>>>>>>>>>>>>>>>>>\n");

    new AgentBuilder.Default()
      .ignore(nameStartsWith("net.bytebuddy.").or(nameStartsWith("sun.reflect.")).or(isSynthetic()), any(), any())
      .disableClassFormatChanges()
      .with(RedefinitionStrategy.RETRANSFORMATION)
      .with(InitializationStrategy.NoOp.INSTANCE)
      .with(TypeStrategy.Default.REDEFINE)
      .type(isSubTypeOf(Tracer.class)
        .or(isSubTypeOf(Scope.class))
        .or(isSubTypeOf(ScopeManager.class))
        .or(isSubTypeOf(Span.class))
        .or(isSubTypeOf(SpanBuilder.class))
        .or(isSubTypeOf(SpanContext.class)))
      .transform(new Transformer() {
        @Override
        public Builder<?> transform(final Builder<?> builder, final TypeDescription typeDescription, final ClassLoader classLoader, final JavaModule module) {
          return builder.visit(Advice.to(TracerMutexAgent.class).on(isPublic().and(any())));
        }})
      .installOn(inst);

    if (logger.isLoggable(Level.FINE))
      logger.fine("\n>>>>>>>>>>>>>>>>>>>>> Installed MutexAgent <<<<<<<<<<<<<<<<<<<<<\n");
  }

  @Advice.OnMethodEnter
  public static void enter() {
    final ThreadLocal<Integer> latch = AgentRule.latch;
    latch.set(latch.get() + 1);
  }

  @Advice.OnMethodExit
  public static void exit() {
    final ThreadLocal<Integer> latch = AgentRule.latch;
    latch.set(latch.get() - 1);
  }
}