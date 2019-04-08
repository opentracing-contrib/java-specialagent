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

package io.opentracing.contrib.specialagent.rabbitmq;

import static net.bytebuddy.matcher.ElementMatchers.*;

import java.util.Arrays;

import io.opentracing.contrib.specialagent.AgentRule;
import io.opentracing.contrib.specialagent.AgentRuleUtil;
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

public class RabbitMQAgentRule implements AgentRule {
  @Override
  public Iterable<? extends AgentBuilder> buildAgent(final String agentArgs) throws Exception {
    final Narrowable builder = new AgentBuilder.Default()
      .ignore(none())
      .with(RedefinitionStrategy.RETRANSFORMATION)
      .with(InitializationStrategy.NoOp.INSTANCE)
      .with(TypeStrategy.Default.REDEFINE)
      .type(hasSuperType(named("com.rabbitmq.client.impl.AMQChannel"))
          .and(not(named("io.opentracing.contrib.rabbitmq.TracingChannel"))));

    return Arrays.asList(
      builder.transform(new Transformer() {
        @Override
        public Builder<?> transform(final Builder<?> builder, final TypeDescription typeDescription, final ClassLoader classLoader, final JavaModule module) {
          return builder.visit(Advice.to(OnEnterPublish.class, OnExitPublish.class).on(named("basicPublish").and(takesArguments(6))));
        }
      }), builder.transform(new Transformer() {
        @Override
        public Builder<?> transform(final Builder<?> builder, final TypeDescription typeDescription, final ClassLoader classLoader, final JavaModule module) {
          return builder.visit(Advice.to(OnExitGet.class).on(named("basicGet")));
        }
      }), builder.transform(new Transformer() {
        @Override
        public Builder<?> transform(final Builder<?> builder, final TypeDescription typeDescription, final ClassLoader classLoader, final JavaModule module) {
          return builder.visit(Advice.to(OnEnterConsume.class).on(named("basicConsume").and(takesArguments(7))));
        }
    }));
  }

  public static class OnEnterConsume {
    @Advice.OnMethodEnter
    public static void enter(@Advice.Argument(value = 6, readOnly = false, typing = Typing.DYNAMIC) Object callback) {
      if (AgentRuleUtil.isEnabled())
        callback = RabbitMQAgentIntercept.enterConsume(callback);
    }
  }

  public static class OnEnterPublish {
    @Advice.OnMethodEnter
    public static void enter(final @Advice.Argument(value = 0, typing = Typing.DYNAMIC) Object exchange, @Advice.Argument(value = 4, readOnly = false, typing = Typing.DYNAMIC) Object props) {
      if (AgentRuleUtil.isEnabled())
        props = RabbitMQAgentIntercept.enterPublish(exchange, props);
    }
  }

  public static class OnExitPublish {
    @Advice.OnMethodExit
    public static void exit() {
      if (AgentRuleUtil.isEnabled())
        RabbitMQAgentIntercept.exitPublish();
    }
  }

  public static class OnExitGet {
    @Advice.OnMethodExit
    public static void exit(final @Advice.Return(readOnly = false, typing = Typing.DYNAMIC) Object returned) {
      if (AgentRuleUtil.isEnabled())
        RabbitMQAgentIntercept.exitGet(returned);
    }
  }
}