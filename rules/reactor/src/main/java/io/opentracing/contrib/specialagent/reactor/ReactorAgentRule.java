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

package io.opentracing.contrib.specialagent.reactor;

import static net.bytebuddy.matcher.ElementMatchers.*;

import java.util.Arrays;

import io.opentracing.contrib.specialagent.AgentRule;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.agent.builder.AgentBuilder.Transformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType.Builder;
import net.bytebuddy.utility.JavaModule;

public class ReactorAgentRule extends AgentRule {
  @Override
  public Iterable<? extends AgentBuilder> buildAgent(final AgentBuilder builder) throws Exception {
    return Arrays.asList(
      builder.type(hasSuperType(named("reactor.core.publisher.Mono")))
        .transform(new Transformer() {
          @Override
          public Builder<?> transform(final Builder<?> builder, final TypeDescription typeDescription, final ClassLoader classLoader, final JavaModule module) {
            return builder.visit(Advice.to(Mono.class).on(named("onAssembly")));
          }}),
      builder.type(hasSuperType(named("reactor.core.publisher.Flux")))
        .transform(new Transformer() {
          @Override
          public Builder<?> transform(final Builder<?> builder, final TypeDescription typeDescription, final ClassLoader classLoader, final JavaModule module) {
            return builder.visit(Advice.to(Flux.class).on(named("onAssembly")));
          }}),
      builder.type(hasSuperType(named("reactor.core.publisher.ParallelFlux")))
        .transform(new Transformer() {
          @Override
          public Builder<?> transform(final Builder<?> builder, final TypeDescription typeDescription, final ClassLoader classLoader, final JavaModule module) {
            return builder.visit(Advice.to(ParallelFlux.class).on(named("onAssembly")));
          }}));
  }

  public static class Mono {
    @Advice.OnMethodEnter
    public static void enter(final @Advice.Origin String origin) {
      if (isEnabled(origin))
        MonoAgentIntercept.enter();
    }
  }

  public static class Flux {
    @Advice.OnMethodEnter
    public static void enter(final @Advice.Origin String origin) {
      if (isEnabled(origin))
        FluxAgentIntercept.enter();
    }
  }

  public static class ParallelFlux {
    @Advice.OnMethodEnter
    public static void enter(final @Advice.Origin String origin) {
      if (isEnabled(origin))
        ParallelFluxAgentIntercept.enter();
    }
  }
}