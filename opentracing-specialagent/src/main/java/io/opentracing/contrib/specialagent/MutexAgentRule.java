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

import java.util.Arrays;
import java.util.List;

import io.opentracing.Scope;
import io.opentracing.ScopeManager;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.Tracer.SpanBuilder;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.agent.builder.AgentBuilder.Identified.Extendable;
import net.bytebuddy.agent.builder.AgentBuilder.Transformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType.Builder;
import net.bytebuddy.utility.JavaModule;

public class MutexAgentRule extends DefaultAgentRule {
  public static final MutexLatch latch = AgentRule.$Access.mutexLatch();

  @Override
  public Iterable<? extends AgentBuilder> buildAgent(final AgentBuilder builder) throws Exception {
    log("\n<<<<<<<<<<<<<<<<<< Installing MutexAgentRule >>>>>>>>>>>>>>>>>>>\n", null, DefaultLevel.FINE);

    final List<Extendable> builders = Arrays.asList(builder
      .type(isSubTypeOf(Tracer.class)
        .or(isSubTypeOf(Scope.class))
        .or(isSubTypeOf(ScopeManager.class))
        .or(isSubTypeOf(Span.class))
        .or(isSubTypeOf(SpanBuilder.class))
        .or(isSubTypeOf(SpanContext.class)))
      .transform(new Transformer() {
        @Override
        public Builder<?> transform(final Builder<?> builder, final TypeDescription typeDescription, final ClassLoader classLoader, final JavaModule module) {
          return builder.visit(Advice.to(MutexAgentRule.class).on(isPublic().and(any())));
        }}));

    log("\n>>>>>>>>>>>>>>>>>>> Installed MutexAgentRule <<<<<<<<<<<<<<<<<<<\n", null, DefaultLevel.FINE);
    return builders;
  }

  @Advice.OnMethodEnter
  public static void enter() {
    latch.set(latch.get() + 1);
  }

  @Advice.OnMethodExit
  public static void exit() {
    latch.set(latch.get() - 1);
  }
}