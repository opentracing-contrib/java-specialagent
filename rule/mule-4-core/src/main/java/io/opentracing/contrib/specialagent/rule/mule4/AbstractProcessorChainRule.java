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

import static net.bytebuddy.matcher.ElementMatchers.*;

import java.util.Collections;

import io.opentracing.contrib.specialagent.AgentRule;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import net.bytebuddy.utility.JavaModule;

public class AbstractProcessorChainRule extends AgentRule {
  @Override
  public Iterable<? extends AgentBuilder> buildAgent(final AgentBuilder builder) throws Exception {
    return Collections.singletonList(builder
      .type(hasSuperType(named("org.mule.runtime.core.privileged.processor.chain.AbstractMessageProcessorChain")))// .and(is(TypeDescription.Generic.Builder.))
      .transform(new AgentBuilder.Transformer() {
        @Override
        public DynamicType.Builder<?> transform(final DynamicType.Builder<?> builder, final TypeDescription typeDescription, final ClassLoader classLoader, final JavaModule module) {
          return builder.visit(Advice.to(AbstractProcessorChainRule.class).on(named("resolveInterceptors")));
        }
      }));
  }

  @Advice.OnMethodExit
  public static void exit(final @Advice.Origin String origin, final @Advice.FieldValue(value = "muleContext") Object muleContext, @Advice.Return(readOnly = false, typing = Assigner.Typing.DYNAMIC) Object interceptors) {
    if (isEnabled(AbstractProcessorChainRule.class.getName(), origin))
      interceptors = AbstractProcessorChainIntercept.exit(muleContext, interceptors);
  }
}