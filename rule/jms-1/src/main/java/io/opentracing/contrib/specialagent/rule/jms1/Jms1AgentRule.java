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

package io.opentracing.contrib.specialagent.rule.jms1;

import static net.bytebuddy.matcher.ElementMatchers.*;

import java.util.Arrays;

import io.opentracing.contrib.common.WrapperProxy;
import io.opentracing.contrib.specialagent.AgentRule;
import io.opentracing.contrib.specialagent.AgentRuleUtil;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.agent.builder.AgentBuilder.Transformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType.Builder;
import net.bytebuddy.implementation.bytecode.assign.Assigner.Typing;
import net.bytebuddy.utility.JavaModule;

public class Jms1AgentRule extends AgentRule {
  @Override
  public Iterable<? extends AgentBuilder> buildAgent(final AgentBuilder builder) {
    return Arrays.asList(builder
      .type(not(isInterface()).and(hasSuperType(named("javax.jms.Session")).and(not(nameStartsWith("io.opentracing.contrib.")))))
      .transform(new Transformer() {
        @Override
        public Builder<?> transform(final Builder<?> builder, final TypeDescription typeDescription, final ClassLoader classLoader, final JavaModule module) {
          return !AgentRuleUtil.hasMethodNamed(typeDescription, "createSharedConsumer") ? builder.visit(Advice.to(Producer.class).on(named("createProducer").and(returns(named("javax.jms.MessageProducer"))))) : builder.visit(Advice.to(Producer.class).on(none()));
        }
      })
      .transform(new Transformer() {
        @Override
        public Builder<?> transform(final Builder<?> builder, final TypeDescription typeDescription, final ClassLoader classLoader, final JavaModule module) {
          return !AgentRuleUtil.hasMethodNamed(typeDescription, "createSharedConsumer") ? builder.visit(Advice.to(Consumer.class).on(named("createConsumer").and(returns(named("javax.jms.MessageConsumer"))))) : builder.visit(Advice.to(Consumer.class).on(none()));
        }
      }));
  }

  public static class Producer {
    @Advice.OnMethodExit
    public static void enter(final @Advice.Origin String origin, @Advice.Return(readOnly = false, typing = Typing.DYNAMIC) Object returned) {
      if (isEnabled("Jms1AgentRule", origin) && !WrapperProxy.isWrapper(returned))
        returned = WrapperProxy.wrap(returned, Jms1AgentIntercept.createProducer(returned));
    }
  }

  public static class Consumer {
    @Advice.OnMethodExit
    public static void enter(final @Advice.Origin String origin, @Advice.Return(readOnly = false, typing = Typing.DYNAMIC) Object returned) {
      if (isEnabled("Jms1AgentRule", origin) && !WrapperProxy.isWrapper(returned))
        returned = WrapperProxy.wrap(returned, Jms1AgentIntercept.createConsumer(returned));
    }
  }
}