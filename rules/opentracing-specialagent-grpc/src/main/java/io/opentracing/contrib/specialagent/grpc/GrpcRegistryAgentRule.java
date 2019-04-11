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

package io.opentracing.contrib.specialagent.grpc;

import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isAbstract;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentracing.contrib.specialagent.AgentRule;
import io.opentracing.contrib.specialagent.AgentRuleUtil;
import java.util.Arrays;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.agent.builder.AgentBuilder.Identified.Narrowable;
import net.bytebuddy.agent.builder.AgentBuilder.Transformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType.Builder;
import net.bytebuddy.implementation.bytecode.assign.Assigner.Typing;
import net.bytebuddy.utility.JavaModule;

public class GrpcRegistryAgentRule extends AgentRule {
  @Override
  public Iterable<? extends AgentBuilder> buildAgent(final String agentArgs,
      final AgentBuilder builder) {
    final Narrowable narrowable = new AgentBuilder.Default()
        .type(hasSuperType(named("io.grpc.HandlerRegistry")))
        .and(not(isAbstract()));

    return Arrays.asList(narrowable.transform(new Transformer() {
      @Override
      public Builder<?> transform(final Builder<?> builder, final TypeDescription typeDescription,
          final ClassLoader classLoader, final JavaModule module) {
        return builder
            .visit(Advice.to(AddService.class).on(named("addService").and(takesArguments(1))));
      }
    }));
  }


  public static class AddService {
    @Advice.OnMethodEnter
    public static void enter(final @Advice.Origin String origin,
        @Advice.Argument(value = 0, readOnly = false, typing = Typing.DYNAMIC) Object service) {
      if (!AgentRuleUtil.isEnabled(origin)) {
        return;
      }
      service = GrpcRegistryAgentIntercept.addService(service);
    }
  }


}