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
package io.opentracing.contrib.specialagent.jms2;

import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;

import io.opentracing.contrib.specialagent.AgentPlugin;
import java.lang.reflect.Method;
import java.util.Arrays;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.agent.builder.AgentBuilder.Identified.Narrowable;
import net.bytebuddy.agent.builder.AgentBuilder.InitializationStrategy;
import net.bytebuddy.agent.builder.AgentBuilder.RedefinitionStrategy;
import net.bytebuddy.agent.builder.AgentBuilder.Transformer;
import net.bytebuddy.agent.builder.AgentBuilder.TypeStrategy;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType.Builder;
import net.bytebuddy.implementation.bytecode.assign.Assigner.Typing;
import net.bytebuddy.utility.JavaModule;

public class Jms2AgentPlugin implements AgentPlugin {
  @Override
  public Iterable<? extends AgentBuilder> buildAgent(final String agentArgs) {
    final Narrowable builder = new AgentBuilder.Default().
      with(RedefinitionStrategy.RETRANSFORMATION).
      with(InitializationStrategy.NoOp.INSTANCE).
      with(TypeStrategy.Default.REDEFINE).
      type(hasSuperType(named("javax.jms.Session")));

    return Arrays.asList(builder.transform(new Transformer() {
      @Override
      public Builder<?> transform(final Builder<?> builder, final TypeDescription typeDescription, final ClassLoader classLoader, final JavaModule module) {
        return builder.visit(Advice.to(Producer.class).on(named("createProducer").and(returns(named("javax.jms.MessageProducer")))));
      }
    }), builder.transform(new Transformer() {
      @Override
      public Builder<?> transform(final Builder<?> builder, final TypeDescription typeDescription, final ClassLoader classLoader, final JavaModule module) {
        return builder.visit(Advice.to(Consumer.class).on(named("createConsumer").and(returns(named("javax.jms.MessageConsumer")))));
      }
    }));
  }

  public static class Producer {
    @Advice.OnMethodExit
    public static void enter(final @Advice.Origin Method method, @Advice.Return(readOnly = false, typing = Typing.DYNAMIC) Object returned) {
      System.out.println(">>>>>> " + method);
      returned = Jms2AgentIntercept.createProducer(returned);
    }
  }

  public static class Consumer {
    @Advice.OnMethodExit
    public static void enter(final @Advice.Origin Method method, @Advice.Return(readOnly = false, typing = Typing.DYNAMIC) Object returned) {
      System.out.println(">>>>>> " + method);
      returned = Jms2AgentIntercept.createConsumer(returned);
    }
  }
}