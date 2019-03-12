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
package io.opentracing.contrib.specialagent.kafka;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentracing.contrib.specialagent.AgentRule;
import io.opentracing.contrib.specialagent.AgentRuleUtil;
import java.util.Arrays;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.agent.builder.AgentBuilder.InitializationStrategy;
import net.bytebuddy.agent.builder.AgentBuilder.RedefinitionStrategy;
import net.bytebuddy.agent.builder.AgentBuilder.Transformer;
import net.bytebuddy.agent.builder.AgentBuilder.TypeStrategy;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType.Builder;
import net.bytebuddy.implementation.bytecode.assign.Assigner.Typing;
import net.bytebuddy.utility.JavaModule;

public class KafkaProducerAgentRule implements AgentRule {
  @Override
  public Iterable<? extends AgentBuilder> buildAgent(final String agentArgs) {
    return Arrays.asList(new AgentBuilder.Default().
        with(RedefinitionStrategy.RETRANSFORMATION).
        with(InitializationStrategy.NoOp.INSTANCE).
        with(TypeStrategy.Default.REDEFINE)
        .type(named("org.apache.kafka.clients.producer.KafkaProducer"))
        .transform(new Transformer() {
          @Override
          public Builder<?> transform(final Builder<?> builder,
              final TypeDescription typeDescription,
              final ClassLoader classLoader, final JavaModule module) {
            return builder
                .visit(Advice.to(KafkaProducerAgentRule.class)
                    .on(named("send").and(takesArguments(2))));
          }
        }));
  }

  @Advice.OnMethodEnter
  public static void enter(
      @Advice.Argument(value = 0, typing = Typing.DYNAMIC) Object record,
      @Advice.Argument(value = 1, readOnly = false, typing = Typing.DYNAMIC) Object callback) {
    if (AgentRuleUtil.isEnabled()) {
      callback = KafkaAgentIntercept.producerCallback(record, callback);
    }
  }

  @Advice.OnMethodExit
  public static void exit() {
    if (AgentRuleUtil.isEnabled()) {
      KafkaAgentIntercept.onProducerExit();
    }
  }
}